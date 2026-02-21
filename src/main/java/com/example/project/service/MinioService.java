package com.example.project.service;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.bucket-posts}")
    private String postsBucket;


    @Value("${minio.bucket-stories}")
    private String storiesBucket;


    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build()
            );
            log.info("Бакет '{}' создан в MinIO", bucket);
        }
    }

    public String uploadPostPhotoBytes(byte[] bytes, String originalFilename, String contentType) {
        try {
            ensureBucketExists(postsBucket);

            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String fileName = "posts/" + UUID.randomUUID() + extension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(postsBucket)
                            .object(fileName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(contentType != null ? contentType : "image/jpeg")
                            .build()
            );

            String fileUrl = minioUrl + "/" + postsBucket + "/" + fileName;
            log.info("Файл успешно загружен в MinIO: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("Ошибка загрузки файла в MinIO: {}", e.getMessage());
            throw new RuntimeException("Не удалось загрузить файл в MinIO: " + e.getMessage());
        }
    }
    

    public String uploadStoryPhotoBytes(byte[] bytes, String originalFilename, String contentType) {
        try {
            ensureBucketExists(storiesBucket);

            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String fileName = "stories/" + UUID.randomUUID() + extension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(storiesBucket)
                            .object(fileName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(contentType != null ? contentType : "image/jpeg")
                            .build()
            );

            String fileUrl = minioUrl + "/" + storiesBucket + "/" + fileName;
            log.info("Story файл успешно загружен в MinIO: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("Ошибка загрузки story файла в MinIO: {}", e.getMessage());
            throw new RuntimeException("Не удалось загрузить файл в MinIO: " + e.getMessage());
        }
    }
}
