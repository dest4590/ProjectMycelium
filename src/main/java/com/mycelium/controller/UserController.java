package com.mycelium.controller;

import com.mycelium.repository.UserRepository;
import com.mycelium.service.UpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserRepository userRepository;
    private final UpdateService updateService;

    public UserController(UserRepository userRepository, UpdateService updateService) {
        this.userRepository = userRepository;
        this.updateService = updateService;
    }


    @PatchMapping("/{username}/scanned")
    @Transactional
    public ResponseEntity<Void> updateUserScannedStatus(
            @PathVariable String username,
            @RequestParam boolean scanned) {

        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setScanned(scanned);
                    if (!scanned) {
                        user.setLastScanned(null);
                    }
                    userRepository.save(user);
                    updateService.sendUserUpdate(user, "manual-update");
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{username}/hidden")
    @Transactional
    public ResponseEntity<Void> updateUserHiddenStatus(
            @PathVariable String username,
            @RequestParam boolean hidden) {

        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setHidden(hidden);
                    userRepository.save(user);
                    updateService.sendUserUpdate(user, "manual-update");
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
