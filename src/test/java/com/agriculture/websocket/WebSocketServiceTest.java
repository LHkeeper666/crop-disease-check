package com.agriculture.websocket;

import com.agriculture.common.websocket.WebSocketMessage;

import com.agriculture.common.websocket.WebSocketService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketService 单元测试")
class WebSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketService webSocketService;

    private Map<String, Object> testData;

    @BeforeEach
    void setUp() {
        testData = new HashMap<>();
        testData.put("id", "test-001");
        testData.put("name", "test-data");
    }

    @Test
    @DisplayName("sendToTopic - 发送消息到指定 Topic")
    void sendToTopic_sendsMessage() {
        WebSocketMessage<Map<String, Object>> message = WebSocketMessage.of("TEST_TYPE", testData);

        webSocketService.sendToTopic("/topic/test", message);

        verify(messagingTemplate).convertAndSend(eq("/topic/test"), eq(message));
    }

    @Test
    @DisplayName("sendToUser - 发送消息到指定用户")
    void sendToUser_sendsMessage() {
        WebSocketMessage<Map<String, Object>> message = WebSocketMessage.of("TEST_TYPE", testData);

        webSocketService.sendToUser("user-001", "/queue/notifications", message);

        verify(messagingTemplate).convertAndSendToUser(eq("user-001"), eq("/queue/notifications"), eq(message));
    }

    @Test
    @DisplayName("sendInferenceResult - 推送推理结果")
    void sendInferenceResult_sendsToCorrectTopic() {
        webSocketService.sendInferenceResult(testData);

        verify(messagingTemplate).convertAndSend(eq("/topic/inference-result"), any(WebSocketMessage.class));
    }

    @Test
    @DisplayName("sendWorkorderChange - 推送工单状态变更")
    void sendWorkorderChange_sendsToCorrectTopic() {
        webSocketService.sendWorkorderChange(testData);

        verify(messagingTemplate).convertAndSend(eq("/topic/workorder-change"), any(WebSocketMessage.class));
    }

    @Test
    @DisplayName("sendHeatmapUpdate - 推送热力图更新")
    void sendHeatmapUpdate_sendsToCorrectTopic() {
        webSocketService.sendHeatmapUpdate(testData);

        verify(messagingTemplate).convertAndSend(eq("/topic/heatmap-update"), any(WebSocketMessage.class));
    }

    @Test
    @DisplayName("sendInspectionStatus - 推送巡检状态")
    void sendInspectionStatus_sendsToCorrectTopic() {
        webSocketService.sendInspectionStatus(testData);

        verify(messagingTemplate).convertAndSend(eq("/topic/inspection-status"), any(WebSocketMessage.class));
    }
}
