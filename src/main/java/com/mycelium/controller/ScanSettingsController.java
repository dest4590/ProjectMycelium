package com.mycelium.controller;

import com.mycelium.service.SeleniumInstagramClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class ScanSettingsController {
    @GetMapping("/limits")
    public ResponseEntity<Map<String, Integer>> getMaxLimits() {
        return ResponseEntity.ok(Map.of(
                "maxFollowers", SeleniumInstagramClient.FOLLOWER_LIMIT,
                "maxFollowing", SeleniumInstagramClient.FOLLOWING_LIMIT
        ));
    }

    @PostMapping("/limits/followers")
    public ResponseEntity<String> updateMaxFollowersLimit(@RequestParam int value) {
        if (value <= 0) {
            return ResponseEntity.badRequest().body("the maximum number of followers must be greater than zero");
        }

        SeleniumInstagramClient.FOLLOWER_LIMIT = value;
        return ResponseEntity.ok("the maximum number of followers has been updated");
    }

    @PostMapping("/limits/following")
    public ResponseEntity<String> updateMaxFollowingLimit(@RequestParam int value) {
        if (value <= 0) {
            return ResponseEntity.badRequest().body("the maximum number of following must be greater than zero");
        }

        SeleniumInstagramClient.FOLLOWING_LIMIT = value;
        return ResponseEntity.ok("the maximum number of following has been updated");
    }
}