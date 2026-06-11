package com.agriculture.modules.workorder.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StatusHistoryVO {

    private String status;
    private LocalDateTime createdAt;
    private String operator;
}
