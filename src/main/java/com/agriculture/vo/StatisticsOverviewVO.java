package com.agriculture.vo;

import lombok.Data;

import java.util.List;

@Data
public class StatisticsOverviewVO {

    private Integer totalReports;
    private Integer todayReports;
    private Integer pendingAudit;
    private Integer processed;
    private Integer highRiskAlerts;
    private List<TypeDistribution> typeDistribution;
    private List<DailyTrend> dailyTrend;
    private List<TopPest> top5Pests;
    private List<TypeDistribution> diseaseDistribution;
    private List<TypeDistribution> pestDistribution;
    private List<TopPest> top5Diseases;
    private List<GridHeatmap> gridHeatmap;

    @Data
    public static class TypeDistribution {
        private String name;
        private Integer value;
    }

    @Data
    public static class DailyTrend {
        private String date;
        private Integer diseaseCount;
        private Integer pestCount;
        private Integer count;
    }

    @Data
    public static class TopPest {
        private String name;
        private Integer count;
    }

    @Data
    public static class GridHeatmap {
        private String gridId;
        private String gridLabel;
        private Double score;
    }
}
