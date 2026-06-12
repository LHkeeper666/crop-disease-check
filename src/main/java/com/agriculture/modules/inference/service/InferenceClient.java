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
import java.time.Duration;

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
            requestBody = objectMapper.writeValueAsString(new DetectRequest(imageUrl, "url", confidence, returnAnnotatedImage));
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
            requestBody = objectMapper.writeValueAsString(new DetectRequest(base64Data, "base64", confidence, returnAnnotatedImage));
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
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/api/v1/detect"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofMillis(readTimeout))
                    .build();

            log.info("调用推理服务: {}/api/v1/detect", serviceUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("推理服务响应状态: {}", response.statusCode());
            JsonNode jsonNode = objectMapper.readTree(response.body());

            if (response.statusCode() != 200) {
                log.warn("推理服务返回非200状态: {}, body: {}", response.statusCode(), response.body());
            }

            return jsonNode;
        } catch (Exception e) {
            log.error("调用推理服务失败", e);
            throw new RuntimeException("调用推理服务失败: " + e.getMessage());
        }
    }

    /**
     * 内部请求体结构
     */
    private static class DetectRequest {
        public final ImageInput image;
        public final float confidence;
        public final boolean return_annotated_image;

        public DetectRequest(String data, String type, float confidence, boolean returnAnnotatedImage) {
            this.image = new ImageInput(type, data);
            this.confidence = confidence;
            this.return_annotated_image = returnAnnotatedImage;
        }
    }

    private static class ImageInput {
        public final String type;
        public final String data;

        public ImageInput(String type, String data) {
            this.type = type;
            this.data = data;
        }
    }
}
