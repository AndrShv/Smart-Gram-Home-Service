package com.example.project.dto.story;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryDTO {

    private String id;

    @NotNull
    private String userId;

    @Size(max = 100)
    private String description;

    private String photoUrl;

    @NotNull
    private String createdAt;

    @NotNull
    private LocalDateTime expireAt;

    @NotNull
    private List<StoryViewerDTO> viewers;

    @Size(max = 10, message = "Нельзя добавить больше 10 тегов")
    private List<@Size(max = 32) String> tags;

    @Size(max = 64)
    private String category;

    @Size(max = 128)
    private String location;

    @Size(max = 64)
    private String mood;

    @Size(max = 128)
    private String overlayText;

    @Size(max = 128)
    private String musicCaption;

    @Builder.Default
    private boolean isPublic = true;

    @JsonIgnore
    @ToString.Exclude
    private transient byte[] photoBytes;

    @JsonIgnore
    @ToString.Exclude
    private transient String photoFileName;
}