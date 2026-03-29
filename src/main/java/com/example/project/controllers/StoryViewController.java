package com.example.project.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/stories")
public class StoryViewController {

    @GetMapping("/create")
    public String showCreateStoryPage(Model model, HttpServletRequest request) {
        log.info("GET /stories/create");
        return "create-story";
    }

    @GetMapping("/preview")
    public String showPreviewPage(HttpServletRequest request) {
        log.info("GET /stories/preview");
        return "story-preview";
    }
}
