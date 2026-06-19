package com.agriculture.modules.workorder.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkOrderVO {

    private Long id;
    private String title;
    private String severity;
    private String status;
    private String type;
    private String gridLabel;
    private String pestName;
    private BigDecimal confidence;
    private String imageUrl;
    private String originalImageUrl;
    private String assignedTo;
    private String assignedToName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
