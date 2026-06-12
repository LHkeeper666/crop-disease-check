package com.agriculture.modules.dailyreport.service;

import com.agriculture.modules.dailyreport.dto.DailyReportGenerateDTO;
import com.agriculture.modules.dailyreport.entity.DailyReport;
import com.agriculture.modules.dailyreport.vo.DailyReportDetailVO;
import com.agriculture.modules.dailyreport.vo.DailyReportVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

public interface DailyReportService extends IService<DailyReport> {

    IPage<DailyReportVO> listReports(LocalDate startDate, LocalDate endDate, int page, int size);

    DailyReportDetailVO getReportDetail(String id);

    String generateReport(DailyReportGenerateDTO dto);
}
