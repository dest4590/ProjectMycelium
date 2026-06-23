package com.mycelium.service;

import com.mycelium.model.ProjectNode;
import com.mycelium.model.UserNode;
import com.mycelium.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InstaService {
    private final ProjectRepository projectRepository;
    private final UpdateService updateService;
    private final UserGraphService userGraphService;
    private final InstagramClient instagramClient;

    @Value("${scan.rescan-after-days:1}")
    private int rescanAfterDays;

    public InstaService(ProjectRepository projectRepository, UpdateService updateService, UserGraphService userGraphService, InstagramClient instagramClient) {
        this.projectRepository = projectRepository;
        this.updateService = updateService;
        this.userGraphService = userGraphService;
        this.instagramClient = instagramClient;
    }

    @Async("taskExecutor")
    public void startRecursiveScan(String startUsername, int maxDepth, String projectName, String taskId, String type) {
        log.info(">>> [TASK ID: {}] STARTING SCAN...", taskId);
        updateService.sendStatus(StatusType.SCAN_STARTED, taskId);
        updateService.sendLog(String.format("project '%s': starting scan of %s with depth %d (type: %s)", projectName, startUsername, maxDepth, type), taskId);

        ProjectNode project = projectRepository.findByName(projectName)
                .orElseGet(() -> projectRepository.save(new ProjectNode(projectName)));

        UserNode startNode = userGraphService.findOrCreateUser(startUsername, project);
        Set<String> staleResult = checkStaleness(startNode, taskId, type);
        if (staleResult != null) {
            updateService.sendLog("scanning completed", taskId);
            updateService.sendStatus(StatusType.SCAN_COMPLETED, taskId);
            return;
        }

        WebDriver driver = null;
        try {
            driver = instagramClient.setupSelenium();
            instagramClient.ensureLoggedIn(driver);

            record QueueEntry(String username, int depth) {
            }

            Deque<QueueEntry> queue = new ArrayDeque<>();
            Set<String> visited = new LinkedHashSet<>();
            queue.add(new QueueEntry(startUsername, 0));

            while (!queue.isEmpty()) {
                QueueEntry entry = queue.poll();
                if (entry.depth() > maxDepth || visited.contains(entry.username())) {
                    continue;
                }
                visited.add(entry.username());

                try {
                    Set<String> nextUsers = processUser(entry.username(), project, taskId, driver, type);
                    if (nextUsers != null && entry.depth() < maxDepth) {
                        for (String next : nextUsers) {
                            if (!visited.contains(next)) {
                                queue.add(new QueueEntry(next, entry.depth() + 1));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("### [TASK ID: {}] error processing {}: {}", taskId, entry.username(), e.getMessage());
                }
            }

            log.info(">>> [TASK ID: {}] SCANNING COMPLETED <<<", taskId);
            updateService.sendLog("scanning completed", taskId);
            updateService.sendStatus(StatusType.SCAN_COMPLETED, taskId);
        } catch (Exception e) {
            log.error("### [TASK ID: {}] CRITICAL ERROR in startRecursiveScan", taskId, e);
            updateService.sendLog("critical error: " + e.getMessage(), taskId);
            updateService.sendStatus(StatusType.SCAN_FAILED, taskId);
        } finally {
            if (driver != null) {
                driver.quit();
                log.info(">>> [TASK ID: {}] Selenium driver has been closed.", taskId);
            }
        }
    }

    public Set<String> processUser(String username, ProjectNode project, String taskId, WebDriver driver, String type) {
        updateService.sendLog(String.format("scanning user %s...", username), taskId);

        UserNode targetNode = userGraphService.findOrCreateUser(username, project);

        Set<String> staleResult = checkStaleness(targetNode, taskId, type);
        if (staleResult != null) return staleResult;

        try {
            if (!navigateAndHandleStatus(username, taskId, driver)) return null;
            if (checkLimits(username, driver, taskId, type)) return null;

            boolean shouldScanFollowers = shouldScanType(type, "followers");
            boolean shouldScanFollowing = shouldScanType(type, "following");

            Set<String> newFollowers = shouldScanFollowers
                    ? instagramClient.getFollowers(username, driver, taskId) : new HashSet<>();
            Set<String> newFollowing = shouldScanFollowing
                    ? instagramClient.getFollowing(username, driver, taskId) : new HashSet<>();

            userGraphService.updateUserRelationships(username, newFollowers, newFollowing, project, taskId, type);
            userGraphService.markUserAsScanned(username, taskId);

            Set<String> usersToScanNext = new HashSet<>(newFollowers);
            usersToScanNext.addAll(newFollowing);
            return usersToScanNext;

        } catch (Exception e) {
            userGraphService.handleScanError(username, e, taskId);
            return null;
        }
    }

    private Set<String> checkStaleness(UserNode targetNode, String taskId, String type) {
        LocalDate staleDate = LocalDate.now().minusDays(rescanAfterDays);
        boolean isRecent = targetNode.getLastScanned() != null && targetNode.getLastScanned().isAfter(staleDate);
        String username = targetNode.getUsername();

        if (targetNode.isScanned() && !type.equals("force") && isRecent) {
            log.warn("... [TASK ID: {}] user {} was scanned recently, skipping", taskId, username);
            updateService.sendLog(String.format("data for %s are still recent, skipping", username), taskId);
            return targetNode.getFollows().stream()
                    .map(rel -> rel.getTargetUser().getUsername())
                    .collect(Collectors.toSet());
        }

        if (targetNode.isScanned() && !type.equals("force")) {
            log.info("... [TASK ID: {}] data for {} are outdated, starting rescan", taskId, username);
            updateService.sendLog(String.format("data for %s are outdated, updating...", username), taskId);
        }

        return null;
    }

    private boolean navigateAndHandleStatus(String username, String taskId, WebDriver driver) {
        InstagramClient.PageStatus status = instagramClient.navigateToProfile(username, driver);

        switch (status) {
            case UNAVAILABLE:
                updateService.sendLog(String.format("profile %s not found or blocked — skipping", username), taskId);
                return false;
            case PRIVATE:
                userGraphService.markUserAsPrivateAndScanned(username, taskId);
                return false;
            case OK:
                return true;
        }
        return true;
    }

    private boolean checkLimits(String username, WebDriver driver, String taskId, String type) {
        if (shouldScanType(type, "followers") && instagramClient.isCountOverLimit(username, driver, taskId, "followers")) {
            userGraphService.markUserAsScanned(username, taskId);
            return true;
        }
        if (shouldScanType(type, "following") && instagramClient.isCountOverLimit(username, driver, taskId, "following")) {
            userGraphService.markUserAsScanned(username, taskId);
            return true;
        }
        return false;
    }

    private static boolean shouldScanType(String type, String target) {
        return type.equals(target) || type.equals("force") || type.equals("default");
    }
}