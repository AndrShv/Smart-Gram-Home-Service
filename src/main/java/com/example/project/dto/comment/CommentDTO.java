package com.example.project.dto.comment;


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
public class CommentDTO {
    private UUID id;
    private UUID userId;

    @NotBlank
    @Size(min = 1, max = 500)
    private String text;

    @NotNull
    private LocalDateTime createdAt;



    @NotNull
    private long reactionsCount;
}
