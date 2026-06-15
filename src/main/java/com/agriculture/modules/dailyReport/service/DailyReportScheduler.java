package com.agriculture.modules.dailyReport.service;

import com.agriculture.modules.dailyReport.dto.DailyReportGenerateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDate;

/**
 * 每日报告自动生成定时任务
 * 每天 23:59 自动聚合当天的检测、工单、巡检数据生成日报
 */
@Component
public class DailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyReportScheduler.class);

    @Resource
    private DailyReportService dailyReportService;

    @Scheduled(cron = "0 59 23 * * ?")
    public void generateDailyReport() {
        LocalDate today = LocalDate.now();
        log.info("[定时任务] 开始生成今日日报: {}", today);

        try {
            DailyReportGenerateDTO dto = new DailyReportGenerateDTO();
            dto.setDate(today);
            String reportId = dailyReportService.generateReport(dto);
            log.info("[定时任务] 日报生成成功: date={}, id={}", today, reportId);
        } catch (Exception e) {
            // 如果日报已存在（手动生成过），跳过
            if (e.getMessage() != null && e.getMessage().contains("已存在")) {
                log.info("[定时任务] 今日日报已存在，跳过: {}", today);
            } else {
                log.error("[定时任务] 日报生成失败: date={}, error={}", today, e.getMessage(), e);
            }
        }
    }
}
