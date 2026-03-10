package com.example.project.proxy;

import com.example.project.clients.AuthClient;
import com.example.project.clients.ProfileClient;
import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.profile.ProfileDTO;
import com.example.project.dto.profile.SubscriberDTO;
import com.example.project.dto.profile.SubscriptionDTO;
import com.example.project.dto.user.UserResponseDTO;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    private final SubscriptionClient subscriptionClient;
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

    @GetMapping("/api/profiles/search")
    public ResponseEntity<?> searchProfiles(@RequestParam String query) {
        try {
            List<ProfileDTO> profiles = profileClient.searchProfiles(query);
            return ResponseEntity.ok(profiles);
        } catch (Exception e) {
            log.error("Ошибка поиска профилей: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/api/subscriptions/{userId}/followers/count")
    public ResponseEntity<Long> getFollowersCount(@PathVariable UUID userId) {
        try {
            return ResponseEntity.ok(subscriptionClient.getFollowersCount(userId));
        } catch (Exception e) {
            log.error("Ошибка получения подписчиков: {}", e.getMessage());
            return ResponseEntity.ok(0L);
        }
    }

    @GetMapping("/api/subscriptions/{userId}/followings/count")
    public ResponseEntity<Long> getFollowingsCount(@PathVariable UUID userId) {
        try {
            return ResponseEntity.ok(subscriptionClient.getFollowingsCount(userId));
        } catch (Exception e) {
            log.error("Ошибка получения подписок: {}", e.getMessage());
            return ResponseEntity.ok(0L);
        }
    }


    @GetMapping("/api/subscriptions/{userId}/followers")
    public ResponseEntity<List<SubscriberDTO>> getFollowers(@PathVariable UUID userId) {
        try {
            return ResponseEntity.ok(subscriptionClient.getFollowers(userId));
        } catch (Exception e) {
            log.error("Ошибка получения подписчиков: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/api/subscriptions/{targetUserId}/subscribe")
    public ResponseEntity<Void> subscribe(@PathVariable UUID targetUserId) {
        try {
            subscriptionClient.subscribe(targetUserId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("уже подписаны")) {
                return ResponseEntity.status(409).build();
            }
            log.error("Ошибка подписки: {}", msg);
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/api/subscriptions/{targetUserId}/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@PathVariable UUID targetUserId) {
        try {
            subscriptionClient.unsubscribe(targetUserId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Ошибка отписки: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
    @GetMapping("/api/subscriptions/{userId}/followings")
    public ResponseEntity<List<SubscriptionDTO>> getFollowings(@PathVariable UUID userId) {
        try {
            return ResponseEntity.ok(subscriptionClient.getFollowings(userId));
        } catch (Exception e) {
            log.error("Ошибка получения подписок: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }
}