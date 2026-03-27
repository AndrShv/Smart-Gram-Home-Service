package com.example.project.exceptions;

public class UnavailableTextForCommentException extends RuntimeException {
    public UnavailableTextForCommentException(String message) {
        super(message);
    }
}
