package com.example.project.dto;


import com.example.project.entity.StoryViewer;
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
    private String userId;
    private String description;
    private String photoUrl;
    private String createdAt;
    private LocalDateTime expireAt;
    private List<StoryViewer> viewers;


}
