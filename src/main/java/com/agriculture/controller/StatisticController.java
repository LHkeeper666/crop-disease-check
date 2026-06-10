package com.agriculture.controller;

import com.agriculture.service.StatisticService;
import com.agriculture.vo.GridStatisticsVO;
import com.agriculture.vo.Result;
import com.agriculture.vo.StatisticsOverviewVO;
import com.agriculture.vo.TrendStatisticsVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/statistics")
public class StatisticController {

    @Resource
    private StatisticService statisticService;

    @GetMapping("/overview")
    public Result<StatisticsOverviewVO> getOverview(
            @RequestParam(required = false) Integer days) {
        return Result.success(statisticService.getOverview(days));
    }

    @GetMapping("/grid")
    public Result<List<GridStatisticsVO>> getGridStatistics(
            @RequestParam(required = false) Integer days) {
        return Result.success(statisticService.getGridStatistics(days));
    }

    @GetMapping("/trend")
    public Result<List<TrendStatisticsVO>> getTrend(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String granularity) {
        return Result.success(statisticService.getTrend(days, granularity));
    }

    @GetMapping("/export")
    public void exportData(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String gridId,
            @RequestParam(required = false) String pestType,
            HttpServletResponse response) {
        statisticService.exportData(startDate, endDate, gridId, pestType, response);
    }
}
