package com.agriculture.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class DailyReportGenerateDTO {

    @NotNull(message = "日期不能为空")
    private LocalDate date;
}
