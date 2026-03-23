package com.example.project.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    @GetMapping({"/", "/home"})
    public String home() {
        log.info("🏠 Открытие главной страницы");
        return "home";
    }

    @GetMapping("/search")
    public String search() {
        log.info("🔍 Открытие страницы поиска");
        return "search";
    }

    @GetMapping("/messages")
    public String messages() {
        log.info("💬 Открытие страницы сообщений");
        return "messages";
    }

    @GetMapping("/settings")
    public String settings() {
        log.info("⚙️ Открытие настроек");
        return "settings";
    }
}
