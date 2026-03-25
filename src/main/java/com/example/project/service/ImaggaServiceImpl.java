package com.example.project.service;

import com.example.project.interfaces.ImaggaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class ImaggaServiceImpl implements ImaggaService {

    private final WebClient imaggaWebClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<String> extractTags(MultipartFile image) {
        try {
            byte[] bytes = image.getBytes();
            String filename = image.getOriginalFilename() != null
                    ? image.getOriginalFilename()
                    : "image.jpg";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename; // Imagga требует имя файла
                }
            });

            String response = imaggaWebClient.post()
                    .uri("/tags")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseTagsFromResponse(response);

        } catch (Exception e) {
            log.error("Полная ошибка Imagga extractTags: ", e);
            throw new RuntimeException("Imagga error", e);
        }
    }

    @Override
    public List<String> extractColorsFromFile(MultipartFile image) {
        try {
            byte[] bytes = image.getBytes();
            String filename = image.getOriginalFilename() != null
                    ? image.getOriginalFilename()
                    : "image.jpg";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            String response = imaggaWebClient.post()
                    .uri("/colors")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseColorsFromResponse(response);

        } catch (Exception e) {
            log.error("Полная ошибка Imagga extractColorsFromFile: ", e);
            throw new RuntimeException("Imagga colors error", e);
        }
    }

    @Override
    public List<String> extractTagsFromUrl(String imageUrl) {
        try {
            String response = imaggaWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tags")
                            .queryParam("image_url", imageUrl)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseTagsFromResponse(response);

        } catch (Exception e) {
            throw new RuntimeException("Imagga error (URL)", e);
        }
    }

    @Override
    public List<String> extractColorsFromUrl(String imageUrl) {
        try {
            String response = imaggaWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/colors")
                            .queryParam("image_url", imageUrl)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseColorsFromResponse(response);

        } catch (Exception e) {
            throw new RuntimeException("Imagga colors error", e);
        }
    }


    private List<String> parseTagsFromResponse(String response) throws Exception {
        List<String> tags = new ArrayList<>();
        JsonNode root = mapper.readTree(response);
        root.path("result").path("tags").forEach(node ->
                tags.add(node.path("tag").path("en").asText())
        );
        return tags;
    }

    private List<String> parseColorsFromResponse(String response) throws Exception {
        List<String> colors = new ArrayList<>();
        JsonNode root = mapper.readTree(response);
        root.path("result").path("colors").path("image_colors").forEach(node ->
                colors.add(node.path("html_code").asText())
        );
        return colors;
    }



    @Override
    public List<String> extractTagsFromBytes(byte[] imageBytes, String filename) {
        try {
            byte[] jpegBytes = convertToJpeg(imageBytes); // ← конвертируем
            String fname = "image.jpg"; // ← всегда jpg

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(jpegBytes) {
                @Override
                public String getFilename() {
                    return fname;
                }
            });

            String response = imaggaWebClient.post()
                    .uri("/tags")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .doOnNext(errorBody -> log.error("❌ Imagga вернул ошибку: {}", errorBody))
                                    .map(errorBody -> new RuntimeException("Imagga error: " + errorBody))
                    )
                    .bodyToMono(String.class)
                    .block();

            return parseTagsFromResponse(response);

        } catch (Exception e) {
            log.error("Ошибка Imagga extractTagsFromBytes: ", e);
            throw new RuntimeException("Imagga error", e);
        }
    }

    @Override
    public List<String> extractColorsFromBytes(byte[] imageBytes, String filename) {
        try {
            byte[] jpegBytes = convertToJpeg(imageBytes); // ← конвертируем
            String fname = "image.jpg";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(jpegBytes) {
                @Override
                public String getFilename() {
                    return fname;
                }
            });

            String response = imaggaWebClient.post()
                    .uri("/colors")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .doOnNext(errorBody -> log.error("❌ Imagga вернул ошибку: {}", errorBody))
                                    .map(errorBody -> new RuntimeException("Imagga colors error: " + errorBody))
                    )
                    .bodyToMono(String.class)
                    .block();

            return parseColorsFromResponse(response);

        } catch (Exception e) {
            log.error("Ошибка Imagga extractColorsFromBytes: ", e);
            throw new RuntimeException("Imagga colors error", e);
        }
    }


    private byte[] convertToJpeg(byte[] imageBytes) {
        try {

            log.info("First bytes: {} {} {} {}",
                    imageBytes[0],
                    imageBytes[1],
                    imageBytes[2],
                    imageBytes[3]);

            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) {
                throw new RuntimeException("ImageIO вернул null — формат не поддерживается");
            }

            BufferedImage rgbImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );


            Graphics2D g = rgbImage.createGraphics();
            g.drawImage(originalImage, 0, 0, Color.WHITE, null);
            g.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "jpg", outputStream);


            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка конвертации изображения:", e);
            throw new RuntimeException("Не удалось конвертировать изображение", e);
        }
    }

}