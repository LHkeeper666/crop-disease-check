package com.agriculture.modules.inference.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 图像检测控制器
 * 桥接前端 DetectionView 与 Python 推理服务 (inference-service)
 *
 * 前端请求路径: /api/v1/detect, /api/v1/detect/batch
 * 本控制器映射: /v1/detect, /v1/detect/batch (因为 context-path = /api)
 */
@RestController
@RequestMapping("/v1/detect")
public class DetectController {

    private static final Logger log = LoggerFactory.getLogger(DetectController.class);

    @Value("${inference.service-url:http://localhost:8000}")
    private String inferenceServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 单张图片检测
     * POST /api/v1/detect
     */
    @PostMapping
    public ResponseEntity<JsonNode> detect(@RequestBody JsonNode requestBody) {
        return forwardToInference("/api/v1/detect", requestBody);
    }

    /**
     * 批量图片检测
     * POST /api/v1/detect/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<JsonNode> detectBatch(@RequestBody JsonNode requestBody) {
        return forwardToInference("/api/v1/detect/batch", requestBody);
    }

    /**
     * 健康检查
     * GET /api/v1/detect/health
     */
    @GetMapping("/health")
    public ResponseEntity<JsonNode> health() {
        return forwardToInference("/api/v1/health", null);
    }

    /**
     * 转发请求到 Python 推理服务
     */
    private ResponseEntity<JsonNode> forwardToInference(String path, JsonNode body) {
        try {
            String url = inferenceServiceUrl + path;
            log.info("转发请求到推理服务: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = null;
            if (body != null) {
                entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            } else {
                entity = new HttpEntity<>(headers);
            }

            HttpMethod method = body != null ? HttpMethod.POST : HttpMethod.GET;
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            log.info("推理服务响应: code={}", responseJson.has("code") ? responseJson.get("code").asInt() : "unknown");

            return ResponseEntity.status(response.getStatusCode()).body(responseJson);
        } catch (Exception e) {
            log.error("调用推理服务失败: {}", e.getMessage(), e);
            JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("code", 500)
                    .put("message", "推理服务不可用: " + e.getMessage())
                    .putNull("data");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
