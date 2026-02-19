package com.example.project.dto;


import com.example.project.entity.StoryViewer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String description;
    @NotBlank
    @NotNull
    private String photoUrl;
    @NotNull
    private String createdAt;
    @NotNull
    private LocalDateTime expireAt;
    @NotNull
    private List<StoryViewer> viewers;

}
