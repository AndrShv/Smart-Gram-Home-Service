package com.example.project.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/posts")
public class PostViewController {
    @GetMapping("/create")
    public String createPost() {
        log.info("GET /stories/posts/create");
        return "create-post";
    }

    @GetMapping("/preview")
    public String previewPost() {
        log.info("GET /stories/posts/preview");
        return "post-preview";
    }
}
