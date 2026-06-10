package com.agriculture.vo;

import lombok.Data;

@Data
public class TrendStatisticsVO {

    private String date;
    private Integer diseaseCount;
    private Integer pestCount;
    private Integer total;
}
