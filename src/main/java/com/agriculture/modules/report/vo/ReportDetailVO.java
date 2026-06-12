package com.agriculture.modules.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上报详情VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDetailVO {

    private String id;
    private String reporterName;
    private String gridLabel;
    private String cropType;
    private List<String> imageUrls;
    private LocalDateTime foundAt;
    private String description;
    private String status;
    private RecognitionResult recognitionResult;
    private AuditInfo auditInfo;
    private PreventionPlan preventionPlan;
    private String rejectReason;
    private LocalDateTime createdAt;

    /**
     * 识别结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecognitionResult {
        private String pestId;
        private String pestName;
        private Double confidence;
        private Boolean isLowConfidence;
        private String pestDescription;
        private String commonConditions;
    }

    /**
     * 审核信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditInfo {
        private String auditorName;
        private LocalDateTime auditedAt;
        private String auditResult;
        private String comment;
    }

    /**
     * 防治方案
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreventionPlan {
        private String content;
        private String suggestTime;
        private String authorName;
        private LocalDateTime createdAt;
        private List<Object> versions;
    }
}
