package com.example.project.exceptions;

public class CommentByPostNotFoundException extends RuntimeException {
    public CommentByPostNotFoundException(String message) {
        super(message);
    }
}
