package com.example.project.clients;

import com.example.project.configs.FeignClientInterceptor;
import com.example.project.dto.post.PostDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "post-service", url = "${POST_SERVICE_URL}", configuration = FeignClientInterceptor.class)
public interface PostClient {

    @GetMapping("/api/posts/{postId}")
    PostDTO getPost(@PathVariable UUID postId);

}
