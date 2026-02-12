package com.mycelium.controller;

import com.mycelium.service.InstaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    private final InstaService instaService;

    public ScanController(InstaService instaService) {
        this.instaService = instaService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startScan(@RequestParam String username, @RequestParam int depth, @RequestParam String projectName, @RequestParam String type) {
        if (projectName == null || projectName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project name must not be empty"));
        }

        String taskId = UUID.randomUUID().toString();
        instaService.startRecursiveScan(username, depth, projectName, taskId, type);

        return ResponseEntity.ok(Map.of("taskId", taskId));
    }
}