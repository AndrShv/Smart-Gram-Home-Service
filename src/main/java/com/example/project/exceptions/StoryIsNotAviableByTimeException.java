package com.example.project.exceptions;

public class StoryIsNotAviableByTimeException extends  RuntimeException {
    public StoryIsNotAviableByTimeException(String message) {
        super(message);
    }
}
