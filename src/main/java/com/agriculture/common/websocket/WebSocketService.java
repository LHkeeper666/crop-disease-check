package com.agriculture.common.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * WebSocket 消息推送服务
 */
@Slf4j
@Service
public class WebSocketService {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 向指定 Topic 广播消息
     *
     * @param topic   目标 Topic（如 /topic/inference-result）
     * @param message 消息内容
     */
    public <T> void sendToTopic(String topic, WebSocketMessage<T> message) {
        log.debug("发送消息到 Topic {}: type={}", topic, message.getType());
        messagingTemplate.convertAndSend(topic, message);
    }

    /**
     * 向指定用户发送消息
     *
     * @param userId  目标用户 ID
     * @param topic   目标 Topic（如 /queue/notifications）
     * @param message 消息内容
     */
    public <T> void sendToUser(String userId, String topic, WebSocketMessage<T> message) {
        log.debug("发送消息到用户 {}: type={}, topic={}", userId, message.getType(), topic);
        messagingTemplate.convertAndSendToUser(userId, topic, message);
    }

    /**
     * 推送推理结果
     */
    public <T> void sendInferenceResult(T data) {
        sendToTopic("/topic/inference-result", WebSocketMessage.of(WebSocketMessageType.INFERENCE_RESULT, data));
    }

    /**
     * 推送工单状态变更
     */
    public <T> void sendWorkorderChange(T data) {
        sendToTopic("/topic/workorder-change", WebSocketMessage.of(WebSocketMessageType.WORKORDER_STATUS_CHANGE, data));
    }

    /**
     * 推送热力图更新
     */
    public <T> void sendHeatmapUpdate(T data) {
        sendToTopic("/topic/heatmap-update", WebSocketMessage.of(WebSocketMessageType.HEATMAP_UPDATE, data));
    }

    /**
     * 推送巡检状态
     */
    public <T> void sendInspectionStatus(T data) {
        sendToTopic("/topic/inspection-status", WebSocketMessage.of(WebSocketMessageType.INSPECTION_STATUS, data));
    }
}
