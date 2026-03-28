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
import java.util.concurrent.ThreadLocalRandom;
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

        WebDriver driver = null;
        try {
            driver = instagramClient.setupSelenium();
            instagramClient.ensureLoggedIn(driver);

            ProjectNode project = projectRepository.findByName(projectName)
                    .orElseGet(() -> projectRepository.save(new ProjectNode(projectName)));

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

        LocalDate staleDate = LocalDate.now().minusDays(rescanAfterDays);
        boolean isRecent = targetNode.getLastScanned() != null && targetNode.getLastScanned().isAfter(staleDate);

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

        try {
            driver.get("https://www.instagram.com/" + username + "/");
            sleep();

            if (instagramClient.isPageUnavailable(driver)) {
                log.warn("[TASK ID: {}] profile {} not found or blocked", taskId, username);
                return null;
            }

            if (instagramClient.isProfilePrivate(driver)) {
                userGraphService.markUserAsPrivateAndScanned(username, taskId);
                return null;
            }

            boolean shouldScanFollowers = type.equals("followers") || type.equals("force") || type.equals("default");
            boolean shouldScanFollowing = type.equals("following") || type.equals("force") || type.equals("default");

            if (shouldScanFollowers && instagramClient.isCountOverLimit(username, driver, taskId, "followers")) {
                userGraphService.markUserAsScanned(username, taskId);
                return null;
            }

            if (shouldScanFollowing && instagramClient.isCountOverLimit(username, driver, taskId, "following")) {
                userGraphService.markUserAsScanned(username, taskId);
                return null;
            }

            Set<String> newFollowers = new HashSet<>();
            Set<String> newFollowing = new HashSet<>();

            if (shouldScanFollowers) {
                newFollowers = instagramClient.getFollowers(username, driver, taskId);
            }
            if (shouldScanFollowing) {
                newFollowing = instagramClient.getFollowing(username, driver, taskId);
            }

            userGraphService.updateUserRelationships(username, newFollowers, newFollowing, project, taskId, type);
            userGraphService.markUserAsScanned(username, taskId);

            Set<String> usersToScanNext = new HashSet<>();
            usersToScanNext.addAll(newFollowers);
            usersToScanNext.addAll(newFollowing);
            return usersToScanNext;

        } catch (Exception e) {
            userGraphService.handleScanError(username, e, taskId);
            return null;
        }
    }

    private void sleep() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong((long) 1500, (long) 2500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}