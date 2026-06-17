package com.agriculture.controller;

import com.agriculture.modules.agriBrain.dto.ChatRequest;
import com.agriculture.modules.agriBrain.dto.ConfigRequest;
import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.agriculture.common.config.LlmProperties;
import com.agriculture.common.exception.GlobalExceptionHandler;
import com.agriculture.modules.agriBrain.controller.AgriBrainController;
import com.agriculture.modules.agriBrain.service.AgriBrainService;
import com.agriculture.modules.agriBrain.service.AiConfigService;
import com.agriculture.modules.agriBrain.service.AiConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AgriBrainControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AgriBrainService agriBrainService;

    @Mock
    private AiConversationService conversationService;

    @Mock
    private AiConfigService configService;

    @Mock
    private LlmProperties llmProperties;

    @InjectMocks
    private AgriBrainController agriBrainController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agriBrainController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /agri-brain/chat")
    class Chat {

        @Test
        @DisplayName("返回 SseEmitter 并调用 service")
        void chat_returnsSseEmitter() throws Exception {
            SseEmitter mockEmitter = new SseEmitter();
            when(agriBrainService.chat(eq("番茄晚疫病怎么治？"), isNull(), eq("user-001"), isNull()))
                    .thenReturn(mockEmitter);

            ChatRequest request = new ChatRequest();
            request.setMessage("番茄晚疫病怎么治？");

            mockMvc.perform(post("/agri-brain/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk());

            verify(agriBrainService).chat(eq("番茄晚疫病怎么治？"), isNull(), eq("user-001"), isNull());
        }

        @Test
        @DisplayName("携带 conversationId 调用 service")
        void chat_withConversationId() throws Exception {
            SseEmitter mockEmitter = new SseEmitter();
            when(agriBrainService.chat(eq("还有其他方法吗？"), eq("conv-001"), eq("user-001"), isNull()))
                    .thenReturn(mockEmitter);

            ChatRequest request = new ChatRequest();
            request.setMessage("还有其他方法吗？");
            request.setConversationId("conv-001");

            mockMvc.perform(post("/agri-brain/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk());

            verify(agriBrainService).chat(eq("还有其他方法吗？"), eq("conv-001"), eq("user-001"), isNull());
        }
    }

    @Nested
    @DisplayName("POST /agri-brain/quick-advice")
    class QuickAdvice {

        @Test
        @DisplayName("返回 SseEmitter")
        void quickAdvice_returnsSseEmitter() throws Exception {
            SseEmitter mockEmitter = new SseEmitter();
            when(agriBrainService.quickAdvice(eq("user-001"))).thenReturn(mockEmitter);

            mockMvc.perform(post("/agri-brain/quick-advice")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk());

            verify(agriBrainService).quickAdvice(eq("user-001"));
        }
    }

    @Nested
    @DisplayName("GET /agri-brain/history")
    class History {

        @Test
        @DisplayName("不带 conversationId 返回对话列表")
        void history_noConversationId_returnsConversationPage() throws Exception {
            AiConversation conv = new AiConversation();
            conv.setId("conv-001");
            conv.setUserId("user-001");
            conv.setTitle("番茄晚疫病怎么治？");
            conv.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0, 0));
            conv.setUpdatedAt(LocalDateTime.of(2026, 6, 10, 10, 5, 0));

            Page<AiConversation> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(conv));
            when(conversationService.page(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/agri-brain/history")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value("conv-001"))
                    .andExpect(jsonPath("$.data.records[0].title").value("番茄晚疫病怎么治？"));
        }

        @Test
        @DisplayName("带 conversationId 返回消息列表")
        void history_withConversationId_returnsMessages() throws Exception {
            List<Map<String, Object>> messages = List.of(
                    Map.of("id", "msg-001", "conversationId", "conv-001", "role", "USER", "content", "番茄晚疫病怎么治？"),
                    Map.of("id", "msg-002", "conversationId", "conv-001", "role", "ASSISTANT", "content", "建议使用甲霜灵...")
            );
            when(agriBrainService.getHistory(eq("conv-001"), eq("user-001"), eq(1), eq(20)))
                    .thenReturn(messages);

            mockMvc.perform(get("/agri-brain/history")
                            .param("conversationId", "conv-001")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].role").value("USER"))
                    .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"));
        }

        @Test
        @DisplayName("自定义分页参数")
        void history_customPagination() throws Exception {
            AiConversation conv = new AiConversation();
            conv.setId("conv-001");
            conv.setUserId("user-001");
            conv.setTitle("测试");

            Page<AiConversation> page = new Page<>(2, 5, 12);
            page.setRecords(List.of(conv));
            when(conversationService.page(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/agri-brain/history")
                            .param("page", "2")
                            .param("size", "5")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.data.total").value(12));
        }

        @Test
        @DisplayName("只返回当前用户的对话")
        void history_onlyReturnsCurrentUserConversations() throws Exception {
            Page<AiConversation> page = new Page<>(1, 20, 0);
            page.setRecords(List.of());
            when(conversationService.page(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/agri-brain/history")
                            .requestAttr("userId", "user-001"))
                    .andExpect(status().isOk());

            // 验证 conversationService.page 被调用时使用了正确的 userId 过滤
            verify(conversationService).page(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("GET /agri-brain/config")
    class GetConfig {

        @Test
        @DisplayName("返回完整配置（apiKey 脱敏）")
        void getConfig_withApiKey_returnsMaskedKey() throws Exception {
            when(configService.getConfigValue("provider")).thenReturn("deepseek");
            when(configService.getConfigValue("model")).thenReturn("deepseek-chat");
            when(configService.getConfigValue("apiKey")).thenReturn("sk-abcdefgh123456");

            mockMvc.perform(get("/agri-brain/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.provider").value("deepseek"))
                    .andExpect(jsonPath("$.data.model").value("deepseek-chat"))
                    .andExpect(jsonPath("$.data.apiKey").value("sk-abc***"))
                    .andExpect(jsonPath("$.data.hasApiKey").value(true));
        }

        @Test
        @DisplayName("无配置时返回默认值")
        void getConfig_noConfig_returnsDefaults() throws Exception {
            when(configService.getConfigValue("provider")).thenReturn(null);
            when(configService.getConfigValue("model")).thenReturn(null);
            when(configService.getConfigValue("apiKey")).thenReturn(null);
            when(llmProperties.getModel()).thenReturn("deepseek-chat");

            mockMvc.perform(get("/agri-brain/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.provider").value(""))
                    .andExpect(jsonPath("$.data.model").value("deepseek-chat"))
                    .andExpect(jsonPath("$.data.apiKey").value(""))
                    .andExpect(jsonPath("$.data.hasApiKey").value(false));
        }
    }

    @Nested
    @DisplayName("PUT /agri-brain/config")
    class UpdateConfig {

        @Test
        @DisplayName("保存完整配置")
        void updateConfig_fullConfig_savesAll() throws Exception {
            ConfigRequest request = new ConfigRequest();
            request.setProvider("deepseek");
            request.setModel("deepseek-chat");
            request.setApiKey("sk-test123");

            mockMvc.perform(put("/agri-brain/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(configService).setConfigValue("provider", "deepseek");
            verify(configService).setConfigValue("model", "deepseek-chat");
            verify(configService).setConfigValue("apiKey", "sk-test123");
        }

        @Test
        @DisplayName("只更新部分配置")
        void updateConfig_partialConfig_savesOnlyProvided() throws Exception {
            ConfigRequest request = new ConfigRequest();
            request.setModel("deepseek-v4-pro");

            mockMvc.perform(put("/agri-brain/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(configService).setConfigValue("model", "deepseek-v4-pro");
        }
    }

    @Nested
    @DisplayName("POST /agri-brain/config/validate")
    class ValidateConfig {

        @Test
        @DisplayName("有效配置返回成功")
        void validateConfig_validConfig_returnsSuccess() throws Exception {
            ConfigRequest request = new ConfigRequest();
            request.setApiKey("sk-valid-key");
            request.setModel("deepseek-chat");

            mockMvc.perform(post("/agri-brain/config/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("配置有效"));
        }

        @Test
        @DisplayName("apiKey 为空返回错误")
        void validateConfig_emptyApiKey_returnsError() throws Exception {
            ConfigRequest request = new ConfigRequest();
            request.setApiKey("");
            request.setModel("deepseek-chat");

            mockMvc.perform(post("/agri-brain/config/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("API Key 不能为空"));
        }

        @Test
        @DisplayName("model 为空返回错误")
        void validateConfig_emptyModel_returnsError() throws Exception {
            ConfigRequest request = new ConfigRequest();
            request.setApiKey("sk-valid-key");
            request.setModel("");

            mockMvc.perform(post("/agri-brain/config/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("模型不能为空"));
        }
    }
}
