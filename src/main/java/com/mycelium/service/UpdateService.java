package com.mycelium.service;

import com.mycelium.model.UserNode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UpdateService {

    private final SimpMessagingTemplate messagingTemplate;

    public UpdateService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendUserUpdate(UserNode user, String taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "USER_NODE_UPDATE");
        payload.put("taskId", taskId);
        payload.put("username", user.getUsername());
        payload.put("isScanned", user.isScanned());
        if (user.getLastScanned() != null) {
            payload.put("lastScanned", user.getLastScanned().toString());
        }
        messagingTemplate.convertAndSend("/topic/updates", (Object) payload);
    }

    public void sendNewEdge(String source, String target, String taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NEW_EDGE");
        payload.put("taskId", taskId);
        payload.put("source", source);
        payload.put("target", target);
        messagingTemplate.convertAndSend("/topic/updates", (Object) payload);
    }

    public void sendLog(String message, String taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LOG_MESSAGE");
        payload.put("taskId", taskId);
        payload.put("message", message);
        messagingTemplate.convertAndSend("/topic/logs", (Object) payload);
    }

    public void sendStatus(StatusType status, String taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", status);
        payload.put("taskId", taskId);
        messagingTemplate.convertAndSend("/topic/status", (Object) payload);
    }

    public void sendNodeDeletion(String username, String taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NODE_DELETION");
        payload.put("taskId", taskId);
        payload.put("username", username);
        messagingTemplate.convertAndSend("/topic/updates", (Object) payload);
    }
}