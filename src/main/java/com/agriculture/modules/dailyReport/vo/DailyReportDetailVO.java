package com.agriculture.modules.dailyreport.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DailyReportDetailVO {

    private String id;
    private LocalDate reportDate;
    private Map<String, Object> summaryJson;
    private String htmlContent;
    private Byte emailSent;
    private LocalDateTime emailSentAt;
    private LocalDateTime createdAt;
}
