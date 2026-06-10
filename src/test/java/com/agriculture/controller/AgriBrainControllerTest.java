package com.agriculture.controller;

import com.agriculture.dto.ChatRequest;
import com.agriculture.entity.AiConversation;
import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.AgriBrainService;
import com.agriculture.service.AiConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AgriBrainControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AgriBrainService agriBrainService;

    @Mock
    private AiConversationService conversationService;

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
            when(agriBrainService.chat(eq("番茄晚疫病怎么治？"), isNull(), eq("system")))
                    .thenReturn(mockEmitter);

            ChatRequest request = new ChatRequest();
            request.setMessage("番茄晚疫病怎么治？");

            mockMvc.perform(post("/agri-brain/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(agriBrainService).chat(eq("番茄晚疫病怎么治？"), isNull(), eq("system"));
        }

        @Test
        @DisplayName("携带 conversationId 调用 service")
        void chat_withConversationId() throws Exception {
            SseEmitter mockEmitter = new SseEmitter();
            when(agriBrainService.chat(eq("还有其他方法吗？"), eq("conv-001"), eq("system")))
                    .thenReturn(mockEmitter);

            ChatRequest request = new ChatRequest();
            request.setMessage("还有其他方法吗？");
            request.setConversationId("conv-001");

            mockMvc.perform(post("/agri-brain/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(agriBrainService).chat(eq("还有其他方法吗？"), eq("conv-001"), eq("system"));
        }
    }

    @Nested
    @DisplayName("POST /agri-brain/quick-advice")
    class QuickAdvice {

        @Test
        @DisplayName("返回 SseEmitter")
        void quickAdvice_returnsSseEmitter() throws Exception {
            SseEmitter mockEmitter = new SseEmitter();
            when(agriBrainService.quickAdvice(eq("system"))).thenReturn(mockEmitter);

            mockMvc.perform(post("/agri-brain/quick-advice"))
                    .andExpect(status().isOk());

            verify(agriBrainService).quickAdvice(eq("system"));
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
            conv.setUserId("system");
            conv.setTitle("番茄晚疫病怎么治？");
            conv.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0, 0));
            conv.setUpdatedAt(LocalDateTime.of(2026, 6, 10, 10, 5, 0));

            Page<AiConversation> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(conv));
            when(conversationService.page(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/agri-brain/history"))
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
            when(agriBrainService.getHistory(eq("conv-001"), eq("system"), eq(1), eq(20)))
                    .thenReturn(messages);

            mockMvc.perform(get("/agri-brain/history").param("conversationId", "conv-001"))
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
            conv.setUserId("system");
            conv.setTitle("测试");

            Page<AiConversation> page = new Page<>(2, 5, 12);
            page.setRecords(List.of(conv));
            when(conversationService.page(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/agri-brain/history").param("page", "2").param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.data.total").value(12));
        }
    }
}
