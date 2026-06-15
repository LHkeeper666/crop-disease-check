package com.agriculture.modules.dailyReport.service;

import com.agriculture.modules.dailyReport.dto.DailyReportGenerateDTO;
import com.agriculture.modules.dailyReport.entity.DailyReport;
import com.agriculture.modules.dailyReport.vo.DailyReportDetailVO;
import com.agriculture.modules.dailyReport.vo.DailyReportVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

public interface DailyReportService extends IService<DailyReport> {

    IPage<DailyReportVO> listReports(LocalDate startDate, LocalDate endDate, int page, int size);

    DailyReportDetailVO getReportDetail(String id);

    String generateReport(DailyReportGenerateDTO dto);
}
