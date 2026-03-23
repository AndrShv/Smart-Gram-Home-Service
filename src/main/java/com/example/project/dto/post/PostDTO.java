package com.example.project.dto.post;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"photoBytes", "photoFileName", "photoFile"})

public class PostDTO {

    private UUID id;
    private UUID userId;

    @Size(min = 1, max = 100)
    private String description;

    private String photoUrl;

    private LocalDateTime createdAt;

    private long viewsCount;
    private long reactionsCount;
    private long commentsCount;

    private List<String> tags;
    private List<String> dominantColors;
    private String category;
    private String location;
    private String mood;
    private boolean isPublic;

    @JsonIgnore
    private transient MultipartFile photoFile;

    @JsonIgnore
    @ToString.Exclude
    private transient byte[] photoBytes;

    @JsonIgnore
    private transient String photoFileName;
}