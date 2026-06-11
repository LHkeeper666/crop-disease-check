package com.agriculture.service;

import com.agriculture.vo.GridStatisticsVO;
import com.agriculture.vo.StatisticsOverviewVO;
import com.agriculture.vo.TrendStatisticsVO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

public interface StatisticService {

    StatisticsOverviewVO getOverview(Integer days);

    List<GridStatisticsVO> getGridStatistics(Integer days);

    List<TrendStatisticsVO> getTrend(Integer days, String granularity);

    void exportData(LocalDate startDate, LocalDate endDate, String gridId, String pestType, HttpServletResponse response);
}
