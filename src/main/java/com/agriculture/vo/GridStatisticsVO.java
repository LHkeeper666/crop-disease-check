package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GridStatisticsVO {

    private String gridId;
    private String gridLabel;
    private Integer totalDetections;
    private Integer diseaseCount;
    private Integer pestCount;
    private BigDecimal avgConfidence;
    private String topPest;
}
