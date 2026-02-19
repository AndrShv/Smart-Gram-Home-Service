package com.example.project.clients;


import com.example.project.configs.FeignClientInterceptor;
import com.example.project.configs.FeignConfig;
import com.example.project.dto.PostDTO;
import com.example.project.dto.ProfileDTO;
import com.example.project.dto.StoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "profile-service",
        url = "${PROFILE_SERVICE_URL}",
        configuration = FeignConfig.class
)
public interface ProfileClient {

    @GetMapping("/api/profiles/user/{userId}/posts")
    List<PostDTO> getPostsByUserId(UUID userId);

    @GetMapping("/api/profiles/user/{userId}/stories")
    List<StoryDTO> getStoriesByUserId(UUID userId);

    @GetMapping("/api/profiles/user/{userId}")
    ProfileDTO getProfileByUserId(@PathVariable("userId") UUID userId);


    @PostMapping(value = "/api/profiles/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, String> uploadAvatar(
            @PathVariable("id") UUID id,
            @RequestPart("file") MultipartFile file
    );

    @GetMapping("/api/profiles/{id}")
    ProfileDTO getProfileById(@PathVariable("id") UUID id);

    @GetMapping("/api/profiles/{id}/avatar")
    byte[] getAvatarById(@PathVariable("id") UUID id);


}
