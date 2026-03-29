package com.example.project.interfaces;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImaggaService {

    List<String> extractTags(MultipartFile image);

    List<String> extractTagsFromUrl(String imageUrl);

    List<String> extractColorsFromUrl(String imageUrl);

    List<String> extractColorsFromFile(MultipartFile image);


    List<String> extractTagsFromBytes(byte[] imageBytes, String filename);
    List<String> extractColorsFromBytes(byte[] imageBytes, String filename);
}