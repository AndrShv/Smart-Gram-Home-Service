package com.example.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImaggaServiceImplTest {

    @Mock
    private WebClient imaggaWebClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @InjectMocks
    private ImaggaServiceImpl imaggaService;

    private static final String TAGS_RESPONSE = """
            {
              "result": {
                "tags": [
                  {"tag": {"en": "cat"}, "confidence": 90},
                  {"tag": {"en": "animal"}, "confidence": 85}
                ]
              }
            }
            """;

    private static final String COLORS_RESPONSE = """
            {
              "result": {
                "colors": {
                  "image_colors": [
                    {"html_code": "#ffffff", "percent": 50},
                    {"html_code": "#000000", "percent": 30}
                  ]
                }
              }
            }
            """;

    private static final String EMPTY_TAGS_RESPONSE = """
            {
              "result": {
                "tags": []
              }
            }
            """;

    // ─── Helper: generate a minimal valid JPEG byte array ───────────────────────

    private byte[] createValidJpegBytes() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    // ─── Helper: mock POST chain ─────────────────────────────────────────────────

    private void mockPostChain(String responseBody) {
        when(imaggaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    private void mockPostChainWithErrorHandling(String responseBody) {
        when(imaggaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    // ─── Helper: mock GET chain ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void mockGetChain(String responseBody) {
        when(imaggaWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // extractTags(MultipartFile)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    void extractTags_returnsParsedTags() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(createValidJpegBytes());
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        mockPostChain(TAGS_RESPONSE);

        List<String> tags = imaggaService.extractTags(file);

        assertThat(tags).containsExactly("cat", "animal");
    }

    @Test
    void extractTags_emptyTagsResponse_returnsEmptyList() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(createValidJpegBytes());
        when(file.getOriginalFilename()).thenReturn(null); // triggers default filename
        mockPostChain(EMPTY_TAGS_RESPONSE);

        List<String> tags = imaggaService.extractTags(file);

        assertThat(tags).isEmpty();
    }

    @Test
    void extractTags_throwsWhenFileReadFails() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new java.io.IOException("disk error"));

        assertThatThrownBy(() -> imaggaService.extractTags(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Imagga error");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // extractColorsFromFile(MultipartFile)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    void extractColorsFromFile_returnsParsedColors() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(createValidJpegBytes());
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        mockPostChain(COLORS_RESPONSE);

        List<String> colors = imaggaService.extractColorsFromFile(file);

        assertThat(colors).containsExactly("#ffffff", "#000000");
    }

    @Test
    void extractColorsFromFile_throwsWhenFileReadFails() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new java.io.IOException("read error"));

        assertThatThrownBy(() -> imaggaService.extractColorsFromFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Imagga colors error");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // extractTagsFromUrl(String)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    void extractTagsFromUrl_returnsParsedTags() {
        mockGetChain(TAGS_RESPONSE);

        List<String> tags = imaggaService.extractTagsFromUrl("https://example.com/image.jpg");

        assertThat(tags).containsExactly("cat", "animal");
    }

    @Test
    void extractTagsFromUrl_emptyResponse_returnsEmptyList() {
        mockGetChain(EMPTY_TAGS_RESPONSE);

        List<String> tags = imaggaService.extractTagsFromUrl("https://example.com/image.jpg");

        assertThat(tags).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // extractColorsFromUrl(String)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    void extractColorsFromUrl_returnsParsedColors() {
        mockGetChain(COLORS_RESPONSE);

        List<String> colors = imaggaService.extractColorsFromUrl("https://example.com/image.jpg");

        assertThat(colors).containsExactly("#ffffff", "#000000");
    }

    @Test
    void extractColorsFromUrl_throwsWhenWebClientFails() {
        when(imaggaWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("connection refused")));

        assertThatThrownBy(() -> imaggaService.extractColorsFromUrl("https://example.com/bad.jpg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Imagga colors error");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // extractTagsFromBytes(byte[], String)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    void extractTagsFromBytes_returnsParsedTags() throws Exception {
        mockPostChainWithErrorHandling(TAGS_RESPONSE);

        List<String> tags = imaggaService.extractTagsFromBytes(createValidJpegBytes(), "image.jpg");

        assertThat(tags).containsExactly("cat", "animal");
    }

    @Test
    void extractTagsFromBytes_throwsWhenInvalidImageBytes() {
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, 0x03};

        assertThatThrownBy(() -> imaggaService.extractTagsFromBytes(garbage, "broken.jpg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Imagga error");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // extractColorsFromBytes(byte[], String)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    void extractColorsFromBytes_returnsParsedColors() throws Exception {
        mockPostChainWithErrorHandling(COLORS_RESPONSE);

        List<String> colors = imaggaService.extractColorsFromBytes(createValidJpegBytes(), "image.jpg");

        assertThat(colors).containsExactly("#ffffff", "#000000");
    }

    @Test
    void extractColorsFromBytes_throwsWhenInvalidImageBytes() {
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, 0x03};

        assertThatThrownBy(() -> imaggaService.extractColorsFromBytes(garbage, "broken.jpg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Imagga colors error");
    }
}