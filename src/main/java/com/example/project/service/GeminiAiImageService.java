package com.example.project.service;

import com.example.project.dto.AiImageResult;
import com.example.project.interfaces.AiImageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.genai.types.GenerateContentConfig;


@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAiImageService implements AiImageService {

    private final Client client;
    private final ObjectMapper objectMapper;

    @Override
    public AiImageResult analyzeImage(byte[] imageBytes) throws Exception {
        client.models.list(null).forEach(model ->
                log.info("Available model: {}", model.name())
        );
        if (imageBytes.length > 5_000_000) {
            throw new RuntimeException("Image too large");
        }

        String prompt = buildPrompt();


        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.7f)
                .build();
        String mimeType = "image/jpeg";
        GenerateContentResponse response = client.models.generateContent(
                "gemini-2.5-flash",
                Content.fromParts(
                        Part.fromText(prompt),
                        Part.fromBytes(imageBytes, mimeType)),
                config
        );

        String text = response.text();

        if (text == null || text.isBlank()) {
            throw new RuntimeException("Gemini returned empty response");
        }
        String json = cleanJson(text);

        try {
            return objectMapper.readValue(json, AiImageResult.class);
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", json, e);
            throw new RuntimeException("Invalid AI response");
        }
    }

    private String buildPrompt() {
        return """
                Analyze this image and return ONLY valid JSON:
                
                {
                  "tags": ["tag1", "tag2", "tag3", "tag4", "tag5"],
                  "colors": ["#RRGGBB", "#RRGGBB"],
                  "caption": "Short engaging social media caption",
                  "storyIdeas": ["idea1", "idea2"]
                }
                
                Rules:
                - Tags: 5-10 relevant keywords
                - Colors: dominant colors in HEX format
                - Caption: max 1 sentence
                - StoryIdeas: exactly 2 short ideas
                - Return ONLY JSON, no text, no explanation
                """;
    }

    private String cleanJson(String text) {
        if (text == null) return null;
        text = text.replace("```json", "")
                .replace("```", "")
                .trim();

        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");

        if (start != -1 && end != -1) {
            return text.substring(start, end + 1);
        }

        return text;
    }
}
