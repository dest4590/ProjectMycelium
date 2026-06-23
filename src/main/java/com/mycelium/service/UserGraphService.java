package com.mycelium.service;

import com.mycelium.model.ProjectNode;
import com.mycelium.model.UserNode;
import com.mycelium.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Slf4j
public class UserGraphService {

    private final UserRepository userRepository;
    private final UpdateService updateService;

    public UserGraphService(UserRepository userRepository, UpdateService updateService) {
        this.userRepository = userRepository;
        this.updateService = updateService;
    }

    @Transactional
    public UserNode findOrCreateUser(String username, ProjectNode project) {
        UserNode user = userRepository.findByUsername(username).orElseGet(() -> new UserNode(username));
        user.addProject(project);
        return userRepository.save(user);
    }

    @Transactional
    public void updateUserRelationships(String username, Set<String> newFollowers, Set<String> newFollowing, ProjectNode project, String taskId, String type) {
        log.info("... [TASK ID: {}] Updating relationships for {} in the database...", taskId, username);

        if (type.equals("following") || type.equals("force") || type.equals("default")) {
            Set<String> oldFollowing = userRepository.findActiveFollowingUsernames(username);

            newFollowing.stream()
                    .filter(followingUsername -> !oldFollowing.contains(followingUsername) && !username.equals(followingUsername))
                    .forEach(followingUsername -> updateService.sendNewEdge(username, followingUsername, taskId));

            userRepository.updateFollowingRelationships(username, newFollowing, project.getName());
        }

        if (type.equals("followers") || type.equals("force") || type.equals("default")) {
            Set<String> oldFollowers = userRepository.findActiveFollowerUsernames(username);

            newFollowers.stream()
                    .filter(followerUsername -> !oldFollowers.contains(followerUsername) && !username.equals(followerUsername))
                    .forEach(followerUsername -> updateService.sendNewEdge(followerUsername, username, taskId));

            userRepository.updateFollowerRelationships(username, newFollowers, project.getName());
        }
    }

    @Transactional
    public void markUserAsScanned(String username, String taskId) {
        userRepository.markAsScanned(username).ifPresent(user -> {
            updateService.sendUserUpdate(user, taskId);
            log.info(">>> [TASK ID: {}] User {} has been marked as scanned.", taskId, username);
        });
    }

    @Transactional
    public void markUserAsPrivateAndScanned(String username, String taskId) {
        userRepository.markAsPrivateAndScanned(username).ifPresent(user -> {
            updateService.sendLog("Profile " + user.getUsername() + " is private — skipping", taskId);
            log.warn("... [TASK ID: {}] profile {} is private, saved to the database", taskId, username);
        });
    }

    @Transactional
    public void handleScanError(String username, Exception e, String taskId) {
        log.error("### [TASK ID: {}] Error while scanning user {}: {}. NOT marking as scanned — will retry.", taskId, username, e.getMessage());
        updateService.sendLog("Error while scanning " + username + ": " + e.getMessage(), taskId);
    }
}