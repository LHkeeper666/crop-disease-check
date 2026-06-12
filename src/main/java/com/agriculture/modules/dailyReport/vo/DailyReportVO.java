package com.agriculture.modules.dailyreport.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyReportVO {

    private String id;
    private LocalDate reportDate;
    private DailyReportSummaryDTO summary;
    private Byte emailSent;
    private LocalDateTime emailSentAt;
    private LocalDateTime createdAt;
}
