package com.agriculture.modules.report.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 我的上报记录查询DTO
 */
@Data
public class ReportQueryDTO {

    /**
     * 状态筛选: PENDING_RECOGNITION/PENDING/AUDITED/REJECTED
     */
    private String status;

    /**
     * 开始日期
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /**
     * 结束日期
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * 页码
     */
    private int page = 1;

    /**
     * 每页条数
     */
    private int size = 20;
}
