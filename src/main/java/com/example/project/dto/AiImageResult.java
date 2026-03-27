package com.example.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiImageResult {
    private List<String> tags;
    private List<String> colors;
    private String description;
    private String caption;
    private List<String> storyIdeas;
    private String sceneAnalysis;
}