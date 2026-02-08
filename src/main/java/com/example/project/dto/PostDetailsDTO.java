package com.example.project.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}

