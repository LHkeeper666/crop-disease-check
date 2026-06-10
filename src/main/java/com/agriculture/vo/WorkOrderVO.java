package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkOrderVO {

    private String id;
    private String title;
    private String severity;
    private String status;
    private String gridLabel;
    private String pestName;
    private BigDecimal confidence;
    private String imageUrl;
    private String assignedToName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
