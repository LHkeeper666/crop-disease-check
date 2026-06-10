package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DailyReportSummaryDTO {

    private Integer totalInspections;
    private Integer totalDetections;
    private Integer diseaseCount;
    private Integer pestCount;
    private BigDecimal workorderHandledRate;
    private List<TopGridDTO> topGrids;
    private List<TopPestDTO> topPests;

    @Data
    public static class TopGridDTO {
        private String gridLabel;
        private Integer count;
    }

    @Data
    public static class TopPestDTO {
        private String name;
        private Integer count;
    }
}
