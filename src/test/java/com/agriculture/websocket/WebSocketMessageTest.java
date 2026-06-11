package com.agriculture.websocket;

import com.agriculture.common.websocket.WebSocketMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage 单元测试")
class WebSocketMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("of - 创建消息")
    void of_createsMessage() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");

        WebSocketMessage<Map<String, Object>> message = WebSocketMessage.of("TEST_TYPE", data);

        assertEquals("TEST_TYPE", message.getType());
        assertEquals(data, message.getData());
    }

    @Test
    @DisplayName("无参构造函数")
    void noArgsConstructor_createsEmptyMessage() {
        WebSocketMessage<String> message = new WebSocketMessage<>();

        assertNull(message.getType());
        assertNull(message.getData());
    }

    @Test
    @DisplayName("全参构造函数")
    void allArgsConstructor_createsMessage() {
        WebSocketMessage<String> message = new WebSocketMessage<>("TYPE", "data");

        assertEquals("TYPE", message.getType());
        assertEquals("data", message.getData());
    }

    @Test
    @DisplayName("JSON 序列化 - 包含 type 和 data")
    void jsonSerialization_includesTypeAndData() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "001");
        data.put("name", "test");

        WebSocketMessage<Map<String, Object>> message = WebSocketMessage.of("INFERENCE_RESULT", data);
        String json = objectMapper.writeValueAsString(message);

        assertTrue(json.contains("\"type\":\"INFERENCE_RESULT\""));
        assertTrue(json.contains("\"id\":\"001\""));
        assertTrue(json.contains("\"name\":\"test\""));
    }

    @Test
    @DisplayName("JSON 反序列化")
    void jsonDeserialization_restoresMessage() throws Exception {
        String json = "{\"type\":\"WORKORDER_STATUS_CHANGE\",\"data\":{\"workorderId\":\"wo-001\"}}";

        WebSocketMessage<Map<String, Object>> message = objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructParametricType(WebSocketMessage.class, Map.class));

        assertEquals("WORKORDER_STATUS_CHANGE", message.getType());
        assertEquals("wo-001", message.getData().get("workorderId"));
    }

    @Test
    @DisplayName("Getter/Setter 测试")
    void getterSetter_worksCorrectly() {
        WebSocketMessage<String> message = new WebSocketMessage<>();

        message.setType("NEW_TYPE");
        message.setData("new-data");

        assertEquals("NEW_TYPE", message.getType());
        assertEquals("new-data", message.getData());
    }
}
