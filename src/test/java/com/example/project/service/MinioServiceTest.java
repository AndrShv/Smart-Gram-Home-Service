package com.example.project.service;

import io.minio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class MinioServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private MinioService minioService;

    private final String bucket = "posts";
    private final String url = "http://localhost:9000";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(minioService, "postsBucket", bucket);
        ReflectionTestUtils.setField(minioService, "minioUrl", url);
    }

    // =========================
    // TEST 1
    // =========================
    @Test
    void upload_success_bucketExists() throws Exception {
        byte[] bytes = "image".getBytes();

        when(minioClient.bucketExists(any())).thenReturn(true);

        String result = minioService.uploadPostPhotoBytes(bytes, "photo.jpg", "image/jpeg");

        assertNotNull(result);
        assertTrue(result.contains(bucket));
        verify(minioClient, times(1)).putObject(any());
        verify(minioClient, never()).makeBucket(any());
    }

    // =========================
    // TEST 2
    // =========================
    @Test
    void upload_success_bucketCreatedIfNotExists() throws Exception {
        byte[] bytes = "image".getBytes();

        when(minioClient.bucketExists(any())).thenReturn(false);

        String result = minioService.uploadPostPhotoBytes(bytes, "photo.jpg", "image/jpeg");

        assertNotNull(result);
        verify(minioClient).makeBucket(any());
        verify(minioClient).putObject(any());
    }

    // =========================
    // TEST 3
    // =========================
    @Test
    void upload_withoutExtension() throws Exception {
        byte[] bytes = "image".getBytes();

        when(minioClient.bucketExists(any())).thenReturn(true);

        String result = minioService.uploadPostPhotoBytes(bytes, "photo", "image/png");

        assertTrue(result.contains("posts/"));
        verify(minioClient).putObject(any());
    }

    // =========================
    // TEST 4
    // =========================
    @Test
    void upload_nullContentType_shouldUseDefault() throws Exception {
        byte[] bytes = "image".getBytes();

        when(minioClient.bucketExists(any())).thenReturn(true);

        minioService.uploadPostPhotoBytes(bytes, "photo.jpg", null);

        ArgumentCaptor<PutObjectArgs> captor =
                ArgumentCaptor.forClass(PutObjectArgs.class);

        verify(minioClient).putObject(captor.capture());

        assertEquals("image/jpeg", captor.getValue().contentType());
    }

    // =========================
    // TEST 5
    // =========================
    @Test
    void upload_shouldThrowException_whenMinioFails() throws Exception {
        byte[] bytes = "image".getBytes();

        when(minioClient.bucketExists(any())).thenReturn(true);
        doThrow(new RuntimeException("Minio down"))
                .when(minioClient).putObject(any());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                minioService.uploadPostPhotoBytes(bytes, "photo.jpg", "image/jpeg")
        );

        assertTrue(ex.getMessage().contains("Не удалось загрузить файл"));
    }
}
