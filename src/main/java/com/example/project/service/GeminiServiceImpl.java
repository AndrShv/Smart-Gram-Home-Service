package com.example.project.service;

import com.example.project.dto.AiImageResult;
import com.example.project.interfaces.AiImageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;
import java.util.regex.*;

@Slf4j
@Service
public class GeminiServiceImpl implements AiImageService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiServiceImpl(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    private final String prompt = """
        Analyze this image and return STRICT JSON:
        {
          "tags": ["..."],
          "colors": ["#..."],
          "description": "...",
          "caption": "...",
          "storyIdeas": ["...","..."],
          "sceneAnalysis": "..."
        }
        Rules:
        - tags: 5-15 keywords
        - colors: 3-6 HEX colors
        - description: 1-2 sentences
        - caption: engaging social media caption with emojis
        - storyIdeas: 3 ideas
        - sceneAnalysis: explain scene
        IMPORTANT: ONLY JSON
    """;

    @Override
    public AiImageResult analyzeImage(byte[] imageBytes) {
        try {
            byte[] jpeg = convertToJpeg(imageBytes);
            String base64 = Base64.getEncoder().encodeToString(jpeg);

            String request = buildRequest(base64);
            String response = callGemini(request);

            String json = extractJson(response);
            return parse(json);

        } catch (Exception e) {
            log.error("Gemini error", e);
            throw new RuntimeException(e);
        }
    }

    private String callGemini(String body) {
        return webClient.post()
                .uri(apiUrl + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String buildRequest(String base64) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        parts.addObject().put("text", prompt);

        ObjectNode img = parts.addObject();
        ObjectNode inline = img.putObject("inlineData");
        inline.put("mimeType", "image/jpeg");
        inline.put("data", base64);

        ObjectNode config = root.putObject("generationConfig");
        config.put("temperature", 0.2);
        config.put("maxOutputTokens", 500);

        return mapper.writeValueAsString(root);
    }

    private String extractJson(String response) throws Exception {
        JsonNode root = mapper.readTree(response);
        String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        Pattern p = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim();

        return text;
    }

    private AiImageResult parse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        return AiImageResult.builder()
                .tags(toList(root.get("tags")))
                .colors(toList(root.get("colors")))
                .description(root.path("description").asText())
                .caption(root.path("caption").asText())
                .storyIdeas(toList(root.get("storyIdeas")))
                .sceneAnalysis(root.path("sceneAnalysis").asText())
                .build();
    }

    private List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return list;
    }

    private byte[] convertToJpeg(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            BufferedImage rgb = new BufferedImage(
                    img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);

            Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, Color.WHITE, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}