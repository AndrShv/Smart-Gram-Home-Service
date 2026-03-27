package com.example.project.service;

import com.example.project.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class CommentAsyncService {
    private final CommentRepository commentRepository;
}
