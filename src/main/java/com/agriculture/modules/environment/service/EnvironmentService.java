package com.agriculture.modules.environment.service;

import com.agriculture.modules.environment.dto.EnvironmentReportDTO;
import com.agriculture.modules.environment.entity.EnvironmentRecord;
import com.agriculture.modules.environment.vo.EnvironmentCurrentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface EnvironmentService extends IService<EnvironmentRecord> {

    /**
     * 获取当前环境数据快照
     */
    EnvironmentCurrentVO getCurrentData(String greenhouseId);

    /**
     * 历史环境数据分页查询
     */
    IPage<EnvironmentRecord> getHistoryData(String greenhouseId, String metrics,
                                             String startDate, String endDate,
                                             int page, int size);

    /**
     * 传感器/手动上报环境数据
     */
    String reportData(EnvironmentReportDTO dto);

    /**
     * 获取最新环境数据（企业隔离）
     */
    List<Map<String, Object>> getLatestRecords(String companyId);

    /**
     * 获取环境数据趋势（企业隔离）
     */
    Map<String, Object> getTrendData(String companyId, int days);
}
