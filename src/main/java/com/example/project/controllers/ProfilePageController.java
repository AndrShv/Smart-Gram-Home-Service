package com.example.project.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfilePageController {

    @GetMapping
    public String myProfile() {
        log.info("👤 Открытие своего профиля");
        return "profile";
    }

    @GetMapping("/{userId}")
    public String userProfile(@PathVariable UUID userId, Model model) {
        log.info("👤 Открытие профиля пользователя: {}", userId);
        model.addAttribute("userId", userId.toString());
        return "profile";
    }
}
