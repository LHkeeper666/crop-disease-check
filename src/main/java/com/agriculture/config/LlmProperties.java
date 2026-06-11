package com.agriculture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-chat";
    private int connectTimeout = 5000;
    private int readTimeout = 60000;
    private int maxHistoryMessages = 20;
}
