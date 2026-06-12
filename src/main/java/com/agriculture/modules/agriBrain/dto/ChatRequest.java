package com.agriculture.modules.agriBrain.dto;

import lombok.Data;

@Data
public class ChatRequest {

    private String message;
    private String conversationId;
}
