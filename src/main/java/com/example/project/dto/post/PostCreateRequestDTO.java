package com.example.project.dto.post;

import jakarta.validation.constraints.Size;
import lombok.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequestDTO {

    @Size(min = 1, max = 100)
    private String description;

    @Size(max = 64)
    private String category;

    @Size(max = 128)
    private String location;

    @Size(max = 64)
    private String mood;

    @Builder.Default
    private boolean isPublic = true;
}