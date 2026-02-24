package com.example.project.proxy;

import com.example.project.clients.AuthClient;
import com.example.project.clients.ProfileClient;
import com.example.project.dto.PostDTO;
import com.example.project.dto.ProfileDTO;
import com.example.project.dto.StoryDTO;
import com.example.project.dto.UserResponseDTO;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProfileApiProxyController {

    private final AuthClient authClient;
    private final ProfileClient profileClient;
    private final MinioClient minioClient;


    @GetMapping("/api/auth/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            UserResponseDTO user = authClient.getCurrentUser();
            return ResponseEntity.ok(Map.of("id", user.getId()));
        } catch (Exception e) {
            log.error("Ошибка получения текущего пользователя: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/api/profiles/user/{userId}")
    public ResponseEntity<?> getProfileByUserId(@PathVariable UUID userId) {
        try {
            ProfileDTO profile = profileClient.getProfileByUserId(userId);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            log.error("Ошибка получения профиля: {}", e.getMessage());
            return ResponseEntity.status(404).build();
        }
    }

    @PostMapping("/api/profiles/{id}/avatar")
    public ResponseEntity<?> uploadAvatar(@PathVariable UUID id,
                                          @RequestParam("file") MultipartFile file) {
        try {
            Map<String, String> result = profileClient.uploadAvatar(id, file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Ошибка загрузки");
        }
    }


    @GetMapping("/api/profiles/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID id) {
        try {
            byte[] imageBytes = profileClient.getAvatarById(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(imageBytes);
        } catch (Exception e) {
            log.error("Ошибка получения аватара: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }


    @GetMapping("/api/proxy/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String imageUrl) {
        try {

            java.net.URI uri = new java.net.URI(imageUrl);
            String path = uri.getPath();
            String withoutLeadingSlash = path.substring(1);
            int firstSlash = withoutLeadingSlash.indexOf('/');
            String bucket = withoutLeadingSlash.substring(0, firstSlash);
            String objectName = withoutLeadingSlash.substring(firstSlash + 1);

            log.debug("Proxy MinIO: bucket={}, object={}", bucket, objectName);

            try (java.io.InputStream stream = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build())) {

                byte[] imageBytes = stream.readAllBytes();
                String contentType = imageUrl.contains(".png") ? "image/png" : "image/jpeg";

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                        .body(imageBytes);
            }
        } catch (Exception e) {
            log.error("Ошибка проксирования изображения {}: {}", imageUrl, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}