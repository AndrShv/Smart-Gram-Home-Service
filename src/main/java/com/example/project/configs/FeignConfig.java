package com.example.project.configs;

import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

@Configuration
@RequiredArgsConstructor
public class FeignConfig {

    private final OAuth2AuthorizedClientService authorizedClientService;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new FeignClientInterceptor(authorizedClientService);
    }
}

