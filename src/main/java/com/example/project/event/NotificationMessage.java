package com.example.project.event;

import com.example.project.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private NotificationType type;
    private String entityId;
    private String actorId;
    private String recipientId;
    private String username;
    private String message;
    private LocalDateTime createdAt;
}
