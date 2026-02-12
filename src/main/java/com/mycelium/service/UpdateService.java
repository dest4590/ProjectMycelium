package com.mycelium.service;

import com.mycelium.model.UserNode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UpdateService {

    private final SimpMessagingTemplate messagingTemplate;

    public UpdateService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendUserUpdate(UserNode user, String taskId) {
        messagingTemplate.convertAndSend("/topic/updates", Map.of(
                "type", "USER_NODE_UPDATE",
                "taskId", taskId,
                "username", user.getUsername(),
                "isScanned", user.isScanned()
        ));
    }

    public void sendNewEdge(String source, String target, String taskId) {
        messagingTemplate.convertAndSend("/topic/updates", Map.of(
                "type", "NEW_EDGE",
                "taskId", taskId,
                "source", source,
                "target", target
        ));
    }

    public void sendLog(String message, String taskId) {
        messagingTemplate.convertAndSend("/topic/logs", Map.of(
                "type", "LOG_MESSAGE",
                "taskId", taskId,
                "message", message
        ));
    }

    public void sendStatus(StatusType status, String taskId) {
        messagingTemplate.convertAndSend("/topic/status", Map.of(
                "type", status,
                "taskId", taskId
        ));
    }

    public void sendNodeDeletion(String username, String taskId) {
        messagingTemplate.convertAndSend("/topic/updates", Map.of(
                "type", "NODE_DELETION",
                "taskId", taskId,
                "username", username
        ));
    }
}