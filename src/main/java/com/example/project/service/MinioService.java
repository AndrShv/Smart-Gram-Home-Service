package com.example.project.service;

import io.minio.*;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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


    @PostConstruct
    public void init() {
        try {
            setBucketPublicPolicy(postsBucket);
            setBucketPublicPolicy(storiesBucket);
        } catch (Exception e) {
            log.error("Ошибка применения политик MinIO: {}", e.getMessage());
        }
    }

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
        setBucketPublicPolicy(bucket);
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

    private void setBucketPublicPolicy(String bucket) {
        try {
            String policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {"AWS": ["*"]},
                            "Action": ["s3:GetObject"],
                            "Resource": ["arn:aws:s3:::%s/*"]
                        }
                    ]
                }
                """.formatted(bucket);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(policy)
                            .build()
            );
            log.info("Публичная политика чтения установлена для бакета '{}'", bucket);
        } catch (Exception e) {
            log.error("Не удалось установить политику для бакета '{}': {}", bucket, e.getMessage());
        }
    }


}
