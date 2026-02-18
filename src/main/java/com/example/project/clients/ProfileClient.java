package com.example.project.clients;


import com.example.project.configs.FeignClientInterceptor;
import com.example.project.configs.FeignConfig;
import com.example.project.dto.PostDTO;
import com.example.project.dto.ProfileDTO;
import com.example.project.dto.StoryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "profile-service",
        url = "${PROFILE_SERVICE_URL}",
        configuration = FeignConfig.class
)
public interface ProfileClient {


    @GetMapping("/api/profiles/user/{userId}")
    ProfileDTO getProfileByUserId(UUID userId);

    @GetMapping("/api/profiles/user/{userId}/posts")
    List<PostDTO> getPostsByUserId(UUID userId);

    @GetMapping("/api/profiles/user/{userId}/stories")
    List<StoryDTO> getStoriesByUserId(UUID userId);


}
