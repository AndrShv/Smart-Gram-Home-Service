package com.example.project.clients;


import com.example.project.configs.FeignClientInterceptor;
import com.example.project.dto.user.ShortUserDTO;
import com.example.project.dto.user.UserResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "auth-service", url = "${AUTH_SERVICE_URL}", configuration = FeignClientInterceptor.class)
public interface AuthClient {

    @GetMapping("/api/users")
    List<ShortUserDTO> getAllUsers();

    @GetMapping("/api/users/{id}")
    ShortUserDTO getUserById(@PathVariable UUID id);

    @GetMapping("/api/auth/me")
    UserResponseDTO getCurrentUser();

    @GetMapping("/api/users/{id}/short")
    ShortUserDTO getShortUser(@PathVariable UUID id);
}
