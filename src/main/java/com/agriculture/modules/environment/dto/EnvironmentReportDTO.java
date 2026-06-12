package com.agriculture.modules.environment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnvironmentReportDTO {

    @NotBlank(message = "greenhouseId不能为空")
    private String greenhouseId;

    private BigDecimal airTemp;
    private BigDecimal soilMoisture;
    private BigDecimal humidity;
    private BigDecimal lightLevel;
    private BigDecimal co2;
    private BigDecimal soilPh;
    private BigDecimal ec;
    private BigDecimal nitrogen;
    private BigDecimal phosphorus;
    private BigDecimal potassium;
    private BigDecimal energyCurrent;
    private BigDecimal energyMax;
}
