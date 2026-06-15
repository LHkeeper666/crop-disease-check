package com.agriculture.modules.statistics.service;

import com.agriculture.modules.grid.vo.GridStatisticsVO;
import com.agriculture.modules.statistics.vo.StatisticsOverviewVO;
import com.agriculture.modules.statistics.vo.TrendStatisticsVO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

public interface StatisticService {

    StatisticsOverviewVO getOverview(Integer days, String companyId);

    List<GridStatisticsVO> getGridStatistics(Integer days, String companyId);

    List<TrendStatisticsVO> getTrend(Integer days, String granularity, String companyId);

    void exportData(LocalDate startDate, LocalDate endDate, String gridId, String pestType, HttpServletResponse response);
}
