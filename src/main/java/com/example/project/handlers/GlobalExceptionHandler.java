package com.example.project.handlers;

import com.example.project.exceptions.StoryIsNotAviableByTimeException;
import com.example.project.exceptions.StoryNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StoryIsNotAviableByTimeException.class)
    public ResponseEntity<String> handleStoryExpired(StoryIsNotAviableByTimeException ex) {
        return ResponseEntity
                .status(HttpStatus.GONE)
                .body(ex.getMessage());
    }

    @ExceptionHandler(StoryNotFoundException.class)
    public ResponseEntity<String> handleNotFound(StoryNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ex.getMessage());
    }


}

