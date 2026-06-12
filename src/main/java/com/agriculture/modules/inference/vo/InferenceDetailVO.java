package com.agriculture.modules.inference.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 识别详情VO（上报详情中使用）
 */
@Data
public class InferenceDetailVO {

    private String id;
    private String reportId;
    private String pestId;
    private String pestName;
    private BigDecimal confidence;
    private Byte isLowConfidence;
    private String pipeline;
    private String bbox;

    /** 病虫害描述 */
    private String pestDescription;
    /** 常见发生条件 */
    private String commonConditions;
}
