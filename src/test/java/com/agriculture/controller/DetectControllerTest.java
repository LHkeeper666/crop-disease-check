package com.agriculture.controller;

import com.agriculture.modules.inference.controller.DetectController;
import com.agriculture.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图像检测控制器测试（代理转发到 Python 推理服务）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DetectController 图像检测控制器测试")
class DetectControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private DetectController detectController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        // DetectController 内部 new RestTemplate()，需要通过反射注入 mock
        Field restTemplateField = DetectController.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(detectController, restTemplate);

        // 注入 inferenceServiceUrl
        Field urlField = DetectController.class.getDeclaredField("inferenceServiceUrl");
        urlField.setAccessible(true);
        urlField.set(detectController, "http://localhost:8000");

        mockMvc = MockMvcBuilders.standaloneSetup(detectController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 辅助方法 ====================

    private String buildSuccessResponse(String message) {
        return "{\"code\":200,\"message\":\"" + message + "\",\"data\":{\"detections\":[]}}";
    }

    private String buildErrorResponse(String message) {
        return "{\"code\":500,\"message\":\"" + message + "\",\"data\":null}";
    }

    // ==================== 单张图片检测 ====================

    @Nested
    @DisplayName("单张图片检测接口")
    class Detect {

        @Test
        @DisplayName("单张图片检测成功")
        void detect_success() throws Exception {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("imageUrl", "http://example.com/img/test.jpg");

            String responseBody = buildSuccessResponse("检测完成");
            ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.exchange(
                    eq("http://localhost:8000/api/v1/detect"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(responseEntity);

            mockMvc.perform(post("/v1/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("检测完成"))
                    .andExpect(jsonPath("$.data.detections").isArray());
        }

        @Test
        @DisplayName("推理服务不可用时返回500")
        void detect_serviceUnavailable_returns500() throws Exception {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("imageUrl", "http://example.com/img/test.jpg");

            when(restTemplate.exchange(
                    eq("http://localhost:8000/api/v1/detect"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(new RestClientException("Connection refused"));

            mockMvc.perform(post("/v1/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("推理服务不可用")));
        }
    }

    // ==================== 批量图片检测 ====================

    @Nested
    @DisplayName("批量图片检测接口")
    class DetectBatch {

        @Test
        @DisplayName("批量图片检测成功")
        void detectBatch_success() throws Exception {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.putArray("images")
                    .add("http://example.com/img/1.jpg")
                    .add("http://example.com/img/2.jpg");

            String responseBody = buildSuccessResponse("批量检测完成");
            ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.exchange(
                    eq("http://localhost:8000/api/v1/detect/batch"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(responseEntity);

            mockMvc.perform(post("/v1/detect/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("批量检测完成"));
        }

        @Test
        @DisplayName("批量检测推理服务异常时返回500")
        void detectBatch_serviceError_returns500() throws Exception {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.putArray("images").add("http://example.com/img/1.jpg");

            when(restTemplate.exchange(
                    eq("http://localhost:8000/api/v1/detect/batch"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(new RestClientException("Service timeout"));

            mockMvc.perform(post("/v1/detect/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("推理服务不可用")));
        }
    }

    // ==================== 健康检查 ====================

    @Nested
    @DisplayName("健康检查接口")
    class Health {

        @Test
        @DisplayName("推理服务健康检查成功")
        void health_success() throws Exception {
            String responseBody = "{\"code\":200,\"message\":\"ok\",\"data\":{\"status\":\"UP\"}}";
            ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.exchange(
                    eq("http://localhost:8000/api/v1/health"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(responseEntity);

            mockMvc.perform(get("/v1/detect/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("UP"));
        }

        @Test
        @DisplayName("推理服务不可用时健康检查返回500")
        void health_serviceUnavailable_returns500() throws Exception {
            when(restTemplate.exchange(
                    eq("http://localhost:8000/api/v1/health"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(new RestClientException("Connection refused"));

            mockMvc.perform(get("/v1/detect/health"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("推理服务不可用")));
        }
    }
}
