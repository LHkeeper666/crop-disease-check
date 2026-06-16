package com.agriculture.service;

import com.agriculture.modules.agriBrain.dto.ChatRequest;
import com.agriculture.modules.agriBrain.dto.PageContext;
import com.agriculture.modules.agriBrain.dto.VisibleData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestContextTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("ChatRequest 反序列化")
    class Deserialize {

        @Test
        @DisplayName("无 context 字段反序列化成功")
        void deserialize_withoutContext_success() throws Exception {
            String json = "{\"message\":\"番茄晚疫病怎么治？\",\"conversationId\":\"conv-001\"}";

            ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

            assertEquals("番茄晚疫病怎么治？", request.getMessage());
            assertEquals("conv-001", request.getConversationId());
            assertNull(request.getContext());
        }

        @Test
        @DisplayName("有完整 context 字段反序列化成功")
        void deserialize_withFullContext_success() throws Exception {
            String json = """
                    {
                      "message": "这个工单怎么处理？",
                      "conversationId": null,
                      "context": {
                        "page": "/workorders",
                        "pageName": "工单管理",
                        "selectedId": "42",
                        "visibleData": {
                          "list": [
                            {"id": 42, "title": "工单1", "status": "pending"},
                            {"id": 38, "title": "工单2", "status": "processing"}
                          ],
                          "stats": {"total": 15, "pending": 5},
                          "filters": {"status": "pending"},
                          "extra": {"topPriority": {"id": 38, "severity": "critical"}}
                        }
                      }
                    }
                    """;

            ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

            assertEquals("这个工单怎么处理？", request.getMessage());
            assertNull(request.getConversationId());
            assertNotNull(request.getContext());

            PageContext ctx = request.getContext();
            assertEquals("/workorders", ctx.getPage());
            assertEquals("工单管理", ctx.getPageName());
            assertEquals("42", ctx.getSelectedId());
            assertNotNull(ctx.getVisibleData());

            VisibleData vd = ctx.getVisibleData();
            assertNotNull(vd.getList());
            assertEquals(2, vd.getList().size());
            assertEquals(42, vd.getList().get(0).get("id"));
            assertNotNull(vd.getStats());
            assertEquals(15, vd.getStats().get("total"));
            assertNotNull(vd.getFilters());
            assertEquals("pending", vd.getFilters().get("status"));
            assertNotNull(vd.getExtra());
        }

        @Test
        @DisplayName("context 只有 page 和 pageName")
        void deserialize_withMinimalContext_success() throws Exception {
            String json = """
                    {
                      "message": "现在整体情况怎么样？",
                      "context": {
                        "page": "/dashboard",
                        "pageName": "遥测总览"
                      }
                    }
                    """;

            ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

            assertNotNull(request.getContext());
            assertEquals("/dashboard", request.getContext().getPage());
            assertEquals("遥测总览", request.getContext().getPageName());
            assertNull(request.getContext().getSelectedId());
            assertNull(request.getContext().getVisibleData());
        }

        @Test
        @DisplayName("context 为 null 反序列化成功")
        void deserialize_withNullContext_success() throws Exception {
            String json = "{\"message\":\"hello\",\"conversationId\":\"conv-001\",\"context\":null}";

            ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

            assertEquals("hello", request.getMessage());
            assertNull(request.getContext());
        }
    }

    @Nested
    @DisplayName("ChatRequest 序列化")
    class Serialize {

        @Test
        @DisplayName("带 context 序列化成功")
        void serialize_withContext_success() throws Exception {
            ChatRequest request = new ChatRequest();
            request.setMessage("测试消息");
            request.setConversationId("conv-001");

            PageContext ctx = new PageContext();
            ctx.setPage("/workorders");
            ctx.setPageName("工单管理");
            ctx.setSelectedId("42");

            VisibleData vd = new VisibleData();
            vd.setStats(Map.of("total", 10));
            vd.setList(List.of(Map.of("id", 1, "title", "工单1")));
            ctx.setVisibleData(vd);

            request.setContext(ctx);

            String json = objectMapper.writeValueAsString(request);

            assertTrue(json.contains("\"page\":\"/workorders\""));
            assertTrue(json.contains("\"selectedId\":\"42\""));
            assertTrue(json.contains("\"total\":10"));
        }

        @Test
        @DisplayName("无 context 序列化 context 为 null")
        void serialize_withoutContext_contextIsNull() throws Exception {
            ChatRequest request = new ChatRequest();
            request.setMessage("测试消息");

            String json = objectMapper.writeValueAsString(request);

            assertTrue(json.contains("\"message\":\"测试消息\""));
            // context 为 null 时可能包含 "context":null，验证反序列化后仍为 null
            ChatRequest deserialized = objectMapper.readValue(json, ChatRequest.class);
            assertNull(deserialized.getContext());
        }
    }
}
