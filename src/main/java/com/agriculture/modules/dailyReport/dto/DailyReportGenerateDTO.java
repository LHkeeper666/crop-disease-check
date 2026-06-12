package com.agriculture.modules.dailyreport.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class DailyReportGenerateDTO {

    @NotNull(message = "日期不能为空")
    private LocalDate date;

    private String greenhouseId;
}
