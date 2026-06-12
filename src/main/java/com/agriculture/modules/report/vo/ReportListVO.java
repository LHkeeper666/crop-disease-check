package com.agriculture.modules.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上报记录列表VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportListVO {

    private String id;
    private String gridLabel;
    private String cropType;
    private List<String> imageUrls;
    private LocalDateTime foundAt;
    private String status;
    private RecognitionResult recognitionResult;
    private AuditInfo auditInfo;
    private LocalDateTime createdAt;

    /**
     * 识别结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecognitionResult {
        private String pestName;
        private Double confidence;
        private Boolean isLowConfidence;
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
        private String comment;
    }
}
