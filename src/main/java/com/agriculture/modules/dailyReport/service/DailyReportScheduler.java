package com.agriculture.modules.dailyReport.service;

import com.agriculture.modules.company.entity.Company;
import com.agriculture.modules.company.mapper.CompanyMapper;
import com.agriculture.modules.dailyReport.dto.DailyReportGenerateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

/**
 * 每日报告自动生成定时任务
 * 每天 23:59 自动聚合当天的检测、工单、巡检数据生成日报（按企业分别生成）
 */
@Component
public class DailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyReportScheduler.class);

    @Resource
    private DailyReportService dailyReportService;

    @Resource
    private CompanyMapper companyMapper;

    @Scheduled(cron = "0 59 23 * * ?")
    public void generateDailyReport() {
        LocalDate today = LocalDate.now();
        log.info("[定时任务] 开始生成今日日报: {}", today);

        List<Company> companies = companyMapper.selectList(null);
        if (companies.isEmpty()) {
            log.warn("[定时任务] 无企业数据，跳过日报生成");
            return;
        }

        for (Company company : companies) {
            try {
                DailyReportGenerateDTO dto = new DailyReportGenerateDTO();
                dto.setDate(today);
                String reportId = dailyReportService.generateReport(dto, company.getId());
                log.info("[定时任务] 日报生成成功: company={}, date={}, id={}", company.getId(), today, reportId);
            } catch (Exception e) {
                log.error("[定时任务] 日报生成失败: company={}, date={}, error={}", company.getId(), today, e.getMessage(), e);
            }
        }
    }
}
