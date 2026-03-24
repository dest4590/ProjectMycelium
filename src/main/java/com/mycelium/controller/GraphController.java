package com.mycelium.controller;

import com.mycelium.model.UserNode;
import com.mycelium.repository.UserRepository;
import com.mycelium.service.UpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/graph")
public class GraphController {
    private final UserRepository userRepository;
    private final UpdateService updateService;

    public GraphController(UserRepository userRepository, UpdateService updateService) {
        this.userRepository = userRepository;
        this.updateService = updateService;
    }

    @GetMapping("/all")
    public Map<String, Object> getAllGraphData(@RequestParam String projectName) {
        List<UserNode> users = userRepository.findAllByProject(projectName);
        return buildGraphFromUsers(users);
    }

    @GetMapping("/shortest-path")
    public List<String> getShortestPath(@RequestParam String start, @RequestParam String end, @RequestParam String projectName) {
        return userRepository.findShortestPathInProject(start, end, projectName);
    }

    @GetMapping("/initial")
    public Map<String, Object> getInitialGraph(@RequestParam String projectName) {
        List<UserNode> initialUsers = userRepository.findTop30ByProject(projectName);
        return buildGraphFromUsers(initialUsers);
    }

    @GetMapping("/expand")
    public Map<String, Object> expandNode(@RequestParam String username, @RequestParam String projectName) {
        List<UserNode> expandedUsers = userRepository.findUserAndNeighborsInProject(username, projectName);
        return buildGraphFromUsers(expandedUsers);
    }

    @DeleteMapping("/{username}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        userRepository.detachDeleteUserByUsername(username);
        updateService.sendNodeDeletion(username, "UserNode");
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> buildGraphFromUsers(List<UserNode> users) {
        Set<String> userNamesInChunk = users.stream()
                .map(UserNode::getUsername)
                .collect(Collectors.toSet());

        var nodes = users.stream()
                .map(user -> Map.of(
                        "username", user.getUsername(),
                        "isPrivate", user.isPrivate(),
                        "scanned", user.isScanned(),
                        "isHidden", user.isHidden()
                ))
                .distinct()
                .toList();

        var edges = users.stream()
                .flatMap(user -> user.getFollows().stream()
                        .filter(rel -> userNamesInChunk.contains(rel.getTargetUser().getUsername()))
                        .map(rel -> Map.of(
                                "source", rel.getTargetUser().getUsername(),
                                "target", user.getUsername(),
                                "active", rel.isActive()
                        )))
                .distinct()
                .toList();

        return Map.of("nodes", nodes, "edges", edges);
    }
}