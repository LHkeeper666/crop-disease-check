package com.agriculture.modules.inference.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python推理服务HTTP客户端
 * 负责调用 inference-service (FastAPI) 的 /api/v1/detect 接口
 */
@Component
public class InferenceClient {

    private static final Logger log = LoggerFactory.getLogger(InferenceClient.class);

    @Value("${inference.service-url:http://localhost:8000}")
    private String serviceUrl;

    @Value("${inference.default-confidence:0.5}")
    private float defaultConfidence;

    @Value("${inference.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${inference.read-timeout:30000}")
    private int readTimeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 单张图片推理（URL方式）
     *
     * @param imageUrl             图片URL地址
     * @param confidence           置信度阈值
     * @param returnAnnotatedImage 是否返回标注图
     * @return 推理服务响应JSON
     */
    public JsonNode detectByUrl(String imageUrl, float confidence, boolean returnAnnotatedImage) {
        String requestBody;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            Map<String, String> image = new LinkedHashMap<>();
            image.put("type", "url");
            image.put("data", imageUrl);
            body.put("image", image);
            body.put("confidence", confidence);
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("构建推理请求失败", e);
            throw new RuntimeException("构建推理请求失败: " + e.getMessage());
        }

        return doDetect(requestBody);
    }

    /**
     * 单张图片推理（Base64方式）
     *
     * @param base64Data           Base64编码的图片数据
     * @param confidence           置信度阈值
     * @param returnAnnotatedImage 是否返回标注图
     * @return 推理服务响应JSON
     */
    public JsonNode detectByBase64(String base64Data, float confidence, boolean returnAnnotatedImage) {
        String requestBody;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            Map<String, String> image = new LinkedHashMap<>();
            image.put("type", "base64");
            image.put("data", base64Data);
            body.put("image", image);
            body.put("confidence", confidence);
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("构建推理请求失败", e);
            throw new RuntimeException("构建推理请求失败: " + e.getMessage());
        }

        return doDetect(requestBody);
    }

    /**
     * 使用默认置信度进行URL推理
     */
    public JsonNode detectByUrl(String imageUrl) {
        return detectByUrl(imageUrl, defaultConfidence, false);
    }

    /**
     * 调用推理服务健康检查
     */
    public JsonNode healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/api/v1/health"))
                    .GET()
                    .timeout(Duration.ofMillis(connectTimeout))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("推理服务健康检查失败", e);
            throw new RuntimeException("推理服务不可用: " + e.getMessage());
        }
    }

    private JsonNode doDetect(String requestBody) {
        if (requestBody == null || requestBody.isEmpty()) {
            throw new RuntimeException("推理请求体为空");
        }
        try {
            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            log.info("调用推理服务: {}/api/v1/detect, bodyBytes={}bytes, startsWith={}...",
                    serviceUrl, bodyBytes.length, requestBody.substring(0, Math.min(80, requestBody.length())));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/api/v1/detect"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .timeout(Duration.ofMillis(readTimeout))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("推理服务响应状态: {}", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("推理服务返回非200状态: {}, body: {}", response.statusCode(), response.body());
                throw new RuntimeException("推理服务返回错误状态: " + response.statusCode() + ", body: " + response.body());
            }

            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("推理服务调用被中断");
            throw new RuntimeException("推理服务调用被中断", e);
        } catch (Exception e) {
            log.error("调用推理服务失败", e);
            throw new RuntimeException("调用推理服务失败: " + e.getMessage());
        }
    }

}
