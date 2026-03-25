package com.example.project.configs;
import com.google.genai.Client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AiClientConfig {

    @Bean
    public Client geminiClient(@Value("${gemini.api.key}") String apiKey) {
        return Client.builder().apiKey(apiKey).build();
    }
}
