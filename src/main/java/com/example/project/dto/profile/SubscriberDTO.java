package com.example.project.dto.profile;

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
public class SubscriberDTO {
    private UUID id;
    private UUID targetUserId;
    private UUID subscriberUserId;
    private LocalDateTime createdAt;
}
