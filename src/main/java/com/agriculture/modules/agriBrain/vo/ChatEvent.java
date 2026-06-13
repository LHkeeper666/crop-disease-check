package com.agriculture.modules.agriBrain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatEvent {

    private String type;
    private String content;
    private String conversationId;
    private String toolCallId;
    private String toolName;

    public static ChatEvent token(String content) {
        return new ChatEvent("token", content, null, null, null);
    }

    public static ChatEvent done(String conversationId) {
        return new ChatEvent("done", null, conversationId, null, null);
    }

    public static ChatEvent error(String message) {
        return new ChatEvent("error", message, null, null, null);
    }

    public static ChatEvent toolCall(String toolCallId, String toolName) {
        return new ChatEvent("tool_call", null, null, toolCallId, toolName);
    }

    public static ChatEvent toolResult(String toolCallId, String toolName, String content) {
        return new ChatEvent("tool_result", content, null, toolCallId, toolName);
    }
}
