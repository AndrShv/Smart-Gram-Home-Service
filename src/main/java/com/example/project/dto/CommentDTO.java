package com.example.project.dto;

import com.example.project.enums.CommentReactions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    private UUID id;
    private UUID userId;
    private String text;
    private LocalDateTime createdAt;
    private UUID postId;
    private UUID parentCommentId;

    @Builder.Default
    private List<CommentReactions> reactions = new ArrayList<>();

    private long reactionsCount;

    @Builder.Default
    private List<CommentDTO> replies = new ArrayList<>();
}