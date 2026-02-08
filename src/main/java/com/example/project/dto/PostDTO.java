package com.example.project.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class PostDTO {

    private UUID id;
    private UUID userId;


    @NotBlank
    @Size(min = 1, max = 100)
    private String description;

    @NotNull
    private String photoUrl;

    @NotNull
    private LocalDateTime createdAt;

    private long viewsCount;
    private long reactionsCount;
    private long commentsCount;
}
