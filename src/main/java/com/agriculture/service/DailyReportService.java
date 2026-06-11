package com.agriculture.service;

import com.agriculture.dto.DailyReportGenerateDTO;
import com.agriculture.entity.DailyReport;
import com.agriculture.vo.DailyReportDetailVO;
import com.agriculture.vo.DailyReportVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

public interface DailyReportService extends IService<DailyReport> {

    IPage<DailyReportVO> listReports(LocalDate startDate, LocalDate endDate, int page, int size);

    DailyReportDetailVO getReportDetail(String id);

    String generateReport(DailyReportGenerateDTO dto);
}
