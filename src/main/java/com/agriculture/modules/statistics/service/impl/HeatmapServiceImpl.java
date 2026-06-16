package com.agriculture.modules.statistics.service.impl;

import com.agriculture.modules.statistics.service.HeatmapService;
import com.agriculture.modules.statistics.vo.StatisticsOverviewVO;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HeatmapServiceImpl implements HeatmapService {

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Override
    public double recalculateGridScore(String gridLabel, String companyId) {
        // 1. 查该网格活跃工单数
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrder::getCompanyId, companyId)
               .eq(WorkOrder::getGridLabel, gridLabel)
               .in(WorkOrder::getStatus, "PENDING", "PROCESSING");
        long count = workOrderMapper.selectCount(wrapper);

        // 2. 查全局最大活跃工单数（用于归一化）
        long maxCount = getMaxActiveOrderCount(companyId);
        if (maxCount == 0) return 0.0;

        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(maxCount), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Override
    public List<StatisticsOverviewVO.GridHeatmap> buildFullHeatmap(String companyId) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(WorkOrder::getStatus, "IGNORED");
        if (companyId != null && !companyId.isEmpty()) {
            wrapper.eq(WorkOrder::getCompanyId, companyId);
        }
        List<WorkOrder> orders = workOrderMapper.selectList(wrapper);

        Map<String, Long> gridCount = orders.stream()
                .filter(w -> w.getGridLabel() != null)
                .collect(Collectors.groupingBy(WorkOrder::getGridLabel, Collectors.counting()));

        if (gridCount.isEmpty()) return Collections.emptyList();

        long maxCount = gridCount.values().stream().max(Long::compareTo).orElse(1L);

        List<StatisticsOverviewVO.GridHeatmap> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : gridCount.entrySet()) {
            StatisticsOverviewVO.GridHeatmap hm = new StatisticsOverviewVO.GridHeatmap();
            hm.setGridId(entry.getKey());
            hm.setGridLabel(entry.getKey());
            hm.setScore(BigDecimal.valueOf(entry.getValue())
                    .divide(BigDecimal.valueOf(maxCount), 2, RoundingMode.HALF_UP)
                    .doubleValue());
            result.add(hm);
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result;
    }

    private long getMaxActiveOrderCount(String companyId) {
        // 按 gridLabel 分组取最大活跃工单数
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrder::getCompanyId, companyId)
               .in(WorkOrder::getStatus, "PENDING", "PROCESSING")
               .isNotNull(WorkOrder::getGridLabel);
        List<WorkOrder> orders = workOrderMapper.selectList(wrapper);

        return orders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getGridLabel, Collectors.counting()))
                .values().stream()
                .max(Long::compareTo)
                .orElse(1L);
    }
}
