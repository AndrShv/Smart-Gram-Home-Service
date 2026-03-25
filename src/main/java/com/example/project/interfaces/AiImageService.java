package com.example.project.interfaces;

import com.example.project.dto.AiImageResult;

public interface AiImageService {
    AiImageResult analyzeImage(byte[] imageBytes);
}