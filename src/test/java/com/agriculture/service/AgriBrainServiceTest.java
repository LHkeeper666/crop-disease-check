package com.agriculture.service;

import com.agriculture.common.config.LlmProperties;
import com.agriculture.modules.agriBrain.entity.AiConversation;
import com.agriculture.modules.agriBrain.entity.AiMessage;
import com.agriculture.modules.agriBrain.service.AiConfigService;
import com.agriculture.modules.agriBrain.service.AiMessageService;
import com.agriculture.modules.agriBrain.service.AiConversationService;
import com.agriculture.modules.agriBrain.service.impl.AgriBrainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgriBrainServiceTest {

    @Mock
    private LlmProperties llmProperties;

    @Mock
    private RestClient llmRestClient;

    @Mock
    private AiConversationService conversationService;

    @Mock
    private AiMessageService messageService;

    @Mock
    private AiConfigService configService;

    @InjectMocks
    private AgriBrainServiceImpl agriBrainService;

    @BeforeEach
    void setUp() {
        lenient().when(llmProperties.getMaxHistoryMessages()).thenReturn(20);
        lenient().when(llmProperties.getModel()).thenReturn("deepseek-chat");
    }

    @Nested
    @DisplayName("getHistory 方法")
    class GetHistory {

        @Test
        @DisplayName("查询指定对话的消息列表")
        void getHistory_withConversationId_returnsMessages() {
            AiMessage userMsg = new AiMessage();
            userMsg.setId("msg-001");
            userMsg.setConversationId("conv-001");
            userMsg.setUserId("system");
            userMsg.setRole("USER");
            userMsg.setContent("番茄晚疫病怎么治？");
            userMsg.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0, 0));

            AiMessage assistantMsg = new AiMessage();
            assistantMsg.setId("msg-002");
            assistantMsg.setConversationId("conv-001");
            assistantMsg.setUserId("system");
            assistantMsg.setRole("ASSISTANT");
            assistantMsg.setContent("建议使用甲霜灵可湿性粉剂...");
            assistantMsg.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0, 5));

            when(messageService.listByConversationId("conv-001", 1000))
                    .thenReturn(List.of(userMsg, assistantMsg));

            List<Map<String, Object>> result = agriBrainService.getHistory("conv-001", "system", 1, 20);

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("msg-001", result.get(0).get("id"));
            assertEquals("USER", result.get(0).get("role"));
            assertEquals("番茄晚疫病怎么治？", result.get(0).get("content"));
            assertEquals("msg-002", result.get(1).get("id"));
            assertEquals("ASSISTANT", result.get(1).get("role"));
        }

        @Test
        @DisplayName("conversationId 为空时返回 null")
        void getHistory_noConversationId_returnsNull() {
            List<Map<String, Object>> result = agriBrainService.getHistory(null, "system", 1, 20);
            assertNull(result);
        }

        @Test
        @DisplayName("conversationId 为空字符串时返回 null")
        void getHistory_blankConversationId_returnsNull() {
            List<Map<String, Object>> result = agriBrainService.getHistory("", "system", 1, 20);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("对话创建逻辑")
    class ConversationCreation {

        @Test
        @DisplayName("createConversation 使用 UUID 创建对话")
        void createConversation_generatesId() {
            AiConversation conv = new AiConversation();
            conv.setId("test-uuid");
            conv.setUserId("system");
            conv.setTitle("测试");
            conv.setCreatedAt(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());

            when(conversationService.createConversation(eq("system"), eq("测试"))).thenReturn(conv);

            AiConversation result = conversationService.createConversation("system", "测试");

            assertNotNull(result.getId());
            assertEquals("system", result.getUserId());
            assertEquals("测试", result.getTitle());
            verify(conversationService).createConversation("system", "测试");
        }
    }

    @Nested
    @DisplayName("消息持久化逻辑")
    class MessagePersistence {

        @Test
        @DisplayName("saveMessage 创建并保存消息")
        void saveMessage_createsMessage() {
            AiMessage msg = new AiMessage();
            msg.setId("msg-new");
            msg.setConversationId("conv-001");
            msg.setUserId("system");
            msg.setRole("USER");
            msg.setContent("测试消息");

            when(messageService.saveMessage(eq("conv-001"), eq("system"), eq("USER"), eq("测试消息")))
                    .thenReturn(msg);

            AiMessage result = messageService.saveMessage("conv-001", "system", "USER", "测试消息");

            assertNotNull(result);
            assertEquals("conv-001", result.getConversationId());
            assertEquals("USER", result.getRole());
            assertEquals("测试消息", result.getContent());
        }

        @Test
        @DisplayName("listByConversationId 按时间正序返回消息")
        void listByConversationId_returnsOrderedMessages() {
            AiMessage msg1 = new AiMessage();
            msg1.setId("msg-001");
            msg1.setRole("USER");
            msg1.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0, 0));

            AiMessage msg2 = new AiMessage();
            msg2.setId("msg-002");
            msg2.setRole("ASSISTANT");
            msg2.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0, 5));

            when(messageService.listByConversationId("conv-001", 20))
                    .thenReturn(List.of(msg1, msg2));

            List<AiMessage> result = messageService.listByConversationId("conv-001", 20);

            assertEquals(2, result.size());
            assertEquals("USER", result.get(0).getRole());
            assertEquals("ASSISTANT", result.get(1).getRole());
        }
    }

    @Nested
    @DisplayName("动态配置读取")
    class DynamicConfig {

        @Test
        @DisplayName("数据库有配置时使用用户配置")
        void getApiKey_withDbConfig_returnsDbValue() {
            when(configService.getConfigValue("apiKey")).thenReturn("sk-user-key");

            // 通过反射测试私有方法 getApiKey()
            String result = invokePrivateMethod(agriBrainService, "getApiKey");

            assertEquals("sk-user-key", result);
        }

        @Test
        @DisplayName("数据库无配置时 fallback 到默认值")
        void getApiKey_noDbConfig_returnsDefault() {
            when(configService.getConfigValue("apiKey")).thenReturn(null);
            when(llmProperties.getApiKey()).thenReturn("sk-default-key");

            String result = invokePrivateMethod(agriBrainService, "getApiKey");

            assertEquals("sk-default-key", result);
        }

        @Test
        @DisplayName("数据库配置为空字符串时 fallback 到默认值")
        void getApiKey_emptyDbConfig_returnsDefault() {
            when(configService.getConfigValue("apiKey")).thenReturn("");
            when(llmProperties.getApiKey()).thenReturn("sk-default-key");

            String result = invokePrivateMethod(agriBrainService, "getApiKey");

            assertEquals("sk-default-key", result);
        }

        @Test
        @DisplayName("数据库有 model 配置时使用用户配置")
        void getModel_withDbConfig_returnsDbValue() {
            when(configService.getConfigValue("model")).thenReturn("deepseek-v4-pro");

            String result = invokePrivateMethod(agriBrainService, "getModel");

            assertEquals("deepseek-v4-pro", result);
        }

        @Test
        @DisplayName("数据库无 model 配置时 fallback 到默认值")
        void getModel_noDbConfig_returnsDefault() {
            when(configService.getConfigValue("model")).thenReturn(null);
            when(llmProperties.getModel()).thenReturn("deepseek-chat");

            String result = invokePrivateMethod(agriBrainService, "getModel");

            assertEquals("deepseek-chat", result);
        }

        @SuppressWarnings("unchecked")
        private <T> T invokePrivateMethod(Object obj, String methodName) {
            try {
                var method = obj.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                return (T) method.invoke(obj);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke " + methodName, e);
            }
        }
    }
}
