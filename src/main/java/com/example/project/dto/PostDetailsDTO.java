package com.example.project.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailsDTO {

    private UUID id;
    private UUID userId;

    private String description;
    private String photoUrl;
    private LocalDateTime createdAt;

    private long viewsCount;
    private long reactionsCount;

    private List<CommentDTO> comments;

    private List<String> tags;
    private List<String> dominantColors;
    private String category;
    private String location;
    private String mood;
    private boolean isPublic;
}