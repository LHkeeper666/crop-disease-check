package com.agriculture.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 消息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 消息数据
     */
    private T data;

    /**
     * 创建消息
     */
    public static <T> WebSocketMessage<T> of(String type, T data) {
        return new WebSocketMessage<>(type, data);
    }
}
