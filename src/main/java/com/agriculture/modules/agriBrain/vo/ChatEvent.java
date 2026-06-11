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

    public static ChatEvent token(String content) {
        return new ChatEvent("token", content, null);
    }

    public static ChatEvent done(String conversationId) {
        return new ChatEvent("done", null, conversationId);
    }

    public static ChatEvent error(String message) {
        return new ChatEvent("error", message, null);
    }
}
