package com.agriculture.modules.pestDiseaseInfo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待审核列表VO
 */
@Data
public class PendingAuditVO {

    private String id;
    private String reportId;
    private String imageUrl;
    private String pestName;
    private BigDecimal confidence;
    private String reporterName;
    private String gridLabel;
    private String cropType;
    private LocalDateTime foundAt;
}
