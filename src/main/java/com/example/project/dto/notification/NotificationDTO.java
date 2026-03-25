package com.example.project.dto.notification;

import com.example.project.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private UUID id;
    private UUID recipientId;
    private UUID actorId;
    private String entityId;
    private NotificationType type;
    private String username;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}