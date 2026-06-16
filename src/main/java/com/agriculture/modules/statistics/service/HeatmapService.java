package com.agriculture.modules.statistics.service;

import com.agriculture.modules.statistics.vo.StatisticsOverviewVO;

import java.util.List;

/**
 * 热力图服务 —— 增量计算 + 全量构建
 */
public interface HeatmapService {

    /**
     * 增量重算单个网格的热力图分数（MQ消费时调用）
     *
     * @param gridLabel 网格标签
     * @param companyId 企业ID
     * @return 归一化分数（0.0 ~ 1.0），无活跃工单时返回 0.0
     */
    double recalculateGridScore(String gridLabel, String companyId);

    /**
     * 全量构建热力图（HTTP API 调用，兼容原逻辑）
     */
    List<StatisticsOverviewVO.GridHeatmap> buildFullHeatmap(String companyId);
}
