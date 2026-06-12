package com.agriculture.modules.agriBrain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private String id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
