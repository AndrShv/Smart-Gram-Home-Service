package com.example.project.event;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostCreatingNotifications {
    private UUID userId;
    private UUID postId;
    private String username;
    private LocalDateTime createdAt;
    private List<UUID> subscriberUserId;
}