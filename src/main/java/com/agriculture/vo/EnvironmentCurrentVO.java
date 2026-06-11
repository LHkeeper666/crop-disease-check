package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EnvironmentCurrentVO {

    private String greenhouseId;
    private String sectorId;
    private EnvironmentData environment;
    private GrowthMetrics growthMetrics;
    private EnergyData energy;
    private LocalDateTime recordedAt;

    @Data
    public static class EnvironmentData {
        private MetricValue airTemp;
        private MetricValue soilMoisture;
        private MetricValue humidity;
        private MetricValue lightLevel;
    }

    @Data
    public static class MetricValue {
        private BigDecimal value;
        private String unit;
        private String status;
        private Threshold threshold;

        public MetricValue(BigDecimal value, String unit, String status, BigDecimal min, BigDecimal max) {
            this.value = value;
            this.unit = unit;
            this.status = status;
            this.threshold = new Threshold(min, max);
        }
    }

    @Data
    public static class Threshold {
        private BigDecimal min;
        private BigDecimal max;

        public Threshold(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }
    }

    @Data
    public static class GrowthMetrics {
        private MetricValue co2;
        private MetricValue soilPh;
        private MetricValue ec;
        private MetricValue temperature;
        private MetricValue nitrogen;
        private MetricValue phosphorus;
        private MetricValue potassium;
    }

    @Data
    public static class EnergyData {
        private BigDecimal current;
        private BigDecimal max;
        private String unit;
        private String trend;
    }
}
