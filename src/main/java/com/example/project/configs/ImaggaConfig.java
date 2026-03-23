package com.example.project.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ImaggaConfig {

    @Bean
    public WebClient imaggaWebClient(
            @Value("${imagga.api.key}") String apiKey,
            @Value("${imagga.api.secret}") String apiSecret
    ) {
        return WebClient.builder()
                .baseUrl("https://api.imagga.com/v2")
                .defaultHeaders(headers -> headers.setBasicAuth(apiKey, apiSecret))
                .build();
    }
}