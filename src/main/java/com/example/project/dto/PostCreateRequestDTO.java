package com.example.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;


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