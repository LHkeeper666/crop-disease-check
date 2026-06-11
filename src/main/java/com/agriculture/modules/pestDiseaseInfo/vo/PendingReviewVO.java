package com.agriculture.modules.pestDiseaseInfo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待复核列表VO（低置信度识别结果）
 */
@Data
public class PendingReviewVO {

    private String id;
    private String reportId;
    private String imageUrl;
    private String pestName;
    private BigDecimal confidence;
    private String reporterName;
    private String gridLabel;
    private LocalDateTime foundAt;
    private LocalDateTime createdAt;
}
