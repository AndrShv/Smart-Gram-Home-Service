package com.example.project.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryViewerDTO {
    private UUID id;
    private UUID viewerId;
    private LocalDateTime viewedAt;
}