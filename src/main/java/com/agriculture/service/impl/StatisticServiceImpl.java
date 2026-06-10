package com.agriculture.service.impl;

import com.agriculture.dao.mapper.*;
import com.agriculture.entity.*;
import com.agriculture.exception.BusinessException;
import com.agriculture.service.StatisticService;
import com.agriculture.vo.GridStatisticsVO;
import com.agriculture.vo.StatisticsOverviewVO;
import com.agriculture.vo.TrendStatisticsVO;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticServiceImpl implements StatisticService {

    @Resource
    private ReportMapper reportMapper;

    @Resource
    private InferenceMapper inferenceMapper;

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Resource
    private GridMapper gridMapper;

    @Resource
    private InspectionLogMapper inspectionLogMapper;

    @Override
    public StatisticsOverviewVO getOverview(Integer days) {
        if (days == null) days = 7;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        StatisticsOverviewVO vo = new StatisticsOverviewVO();

        // 总上报数
        LambdaQueryWrapper<Report> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.ge(Report::getCreatedAt, startTime).le(Report::getCreatedAt, now);
        vo.setTotalReports(reportMapper.selectCount(reportWrapper).intValue());

        // 今日上报数
        LambdaQueryWrapper<Report> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(Report::getCreatedAt, todayStart).le(Report::getCreatedAt, now);
        vo.setTodayReports(reportMapper.selectCount(todayWrapper).intValue());

        // 待审核数 (PENDING / PENDING_RECOGNITION)
        LambdaQueryWrapper<Report> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.in(Report::getStatus, "PENDING", "PENDING_RECOGNITION");
        vo.setPendingAudit(reportMapper.selectCount(pendingWrapper).intValue());

        // 已处理数
        LambdaQueryWrapper<Report> processedWrapper = new LambdaQueryWrapper<>();
        processedWrapper.eq(Report::getStatus, "AUDITED");
        vo.setProcessed(reportMapper.selectCount(processedWrapper).intValue());

        // 高风险告警 (CRITICAL 且未 DONE)
        LambdaQueryWrapper<WorkOrder> alertWrapper = new LambdaQueryWrapper<>();
        alertWrapper.eq(WorkOrder::getSeverity, "CRITICAL")
                    .ne(WorkOrder::getStatus, "DONE");
        vo.setHighRiskAlerts(workOrderMapper.selectCount(alertWrapper).intValue());

        // 查询时间窗口内的所有 inference
        LambdaQueryWrapper<Inference> inferenceWrapper = new LambdaQueryWrapper<>();
        inferenceWrapper.ge(Inference::getCreatedAt, startTime).le(Inference::getCreatedAt, now);
        List<Inference> inferences = inferenceMapper.selectList(inferenceWrapper);

        // 类型分布 (按病虫害名称)
        Map<String, Long> typeCount = inferences.stream()
                .filter(i -> i.getPestName() != null)
                .collect(Collectors.groupingBy(Inference::getPestName, Collectors.counting()));
        List<StatisticsOverviewVO.TypeDistribution> typeDistribution = typeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    StatisticsOverviewVO.TypeDistribution td = new StatisticsOverviewVO.TypeDistribution();
                    td.setName(entry.getKey());
                    td.setValue(entry.getValue().intValue());
                    return td;
                })
                .collect(Collectors.toList());
        vo.setTypeDistribution(typeDistribution);

        // 每日趋势 (含病害/虫害拆分)
        Map<LocalDate, List<Inference>> dailyGroups = inferences.stream()
                .filter(i -> i.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getCreatedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));
        List<StatisticsOverviewVO.DailyTrend> dailyTrend = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Inference>> entry : dailyGroups.entrySet()) {
            StatisticsOverviewVO.DailyTrend trend = new StatisticsOverviewVO.DailyTrend();
            trend.setDate(entry.getKey().toString());
            List<Inference> dayList = entry.getValue();
            int disease = (int) dayList.stream().filter(i -> "DISEASE".equals(i.getPipeline())).count();
            int pest = (int) dayList.stream().filter(i -> "PEST".equals(i.getPipeline())).count();
            trend.setDiseaseCount(disease);
            trend.setPestCount(pest);
            trend.setCount(dayList.size());
            dailyTrend.add(trend);
        }
        vo.setDailyTrend(dailyTrend);

        // Top5 病虫害
        Map<String, Long> pestNameCount = inferences.stream()
                .filter(i -> i.getPestName() != null)
                .collect(Collectors.groupingBy(Inference::getPestName, Collectors.counting()));
        List<StatisticsOverviewVO.TopPest> top5Pests = pestNameCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    StatisticsOverviewVO.TopPest tp = new StatisticsOverviewVO.TopPest();
                    tp.setName(entry.getKey());
                    tp.setCount(entry.getValue().intValue());
                    return tp;
                })
                .collect(Collectors.toList());
        vo.setTop5Pests(top5Pests);

        // 网格热力图: Inference -> Report -> Grid
        List<StatisticsOverviewVO.GridHeatmap> gridHeatmap = buildGridHeatmap(inferences);
        vo.setGridHeatmap(gridHeatmap);

        return vo;
    }

    @Override
    public List<GridStatisticsVO> getGridStatistics(Integer days) {
        if (days == null) days = 7;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days);

        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Inference::getCreatedAt, startTime).le(Inference::getCreatedAt, now);
        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        if (inferences.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取所有 reportId -> gridId 映射
        Set<String> reportIds = inferences.stream()
                .map(Inference::getReportId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (reportIds.isEmpty()) return Collections.emptyList();

        Map<String, String> reportGridMap = getReportGridMap(reportIds);
        Set<String> gridIds = new HashSet<>(reportGridMap.values());
        Map<String, String> gridLabelMap = getGridLabelMap(gridIds);

        // 按 gridId 分组
        Map<String, List<Inference>> gridInferences = inferences.stream()
                .filter(i -> i.getReportId() != null && reportGridMap.containsKey(i.getReportId()))
                .collect(Collectors.groupingBy(i -> reportGridMap.get(i.getReportId())));

        List<GridStatisticsVO> result = new ArrayList<>();
        for (Map.Entry<String, List<Inference>> entry : gridInferences.entrySet()) {
            String gridId = entry.getKey();
            List<Inference> gridList = entry.getValue();

            GridStatisticsVO vo = new GridStatisticsVO();
            vo.setGridId(gridId);
            vo.setGridLabel(gridLabelMap.getOrDefault(gridId, gridId));
            vo.setTotalDetections(gridList.size());
            vo.setDiseaseCount((int) gridList.stream().filter(i -> "DISEASE".equals(i.getPipeline())).count());
            vo.setPestCount((int) gridList.stream().filter(i -> "PEST".equals(i.getPipeline())).count());

            // 平均置信度
            BigDecimal avgConf = gridList.stream()
                    .map(Inference::getConfidence)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!gridList.isEmpty()) {
                vo.setAvgConfidence(avgConf.divide(BigDecimal.valueOf(gridList.size()), 4, RoundingMode.HALF_UP));
            } else {
                vo.setAvgConfidence(BigDecimal.ZERO);
            }

            // 最高频病虫害
            String topPest = gridList.stream()
                    .filter(i -> i.getPestName() != null)
                    .collect(Collectors.groupingBy(Inference::getPestName, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            vo.setTopPest(topPest);

            result.add(vo);
        }

        // 按总检测数降序
        result.sort((a, b) -> Integer.compare(b.getTotalDetections(), a.getTotalDetections()));
        return result;
    }

    @Override
    public List<TrendStatisticsVO> getTrend(Integer days, String granularity) {
        if (days == null) days = 30;
        if (granularity == null) granularity = "DAY";

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days);

        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Inference::getCreatedAt, startTime).le(Inference::getCreatedAt, now);
        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        // 按粒度分组
        Map<String, List<Inference>> grouped;
        switch (granularity) {
            case "WEEK":
                grouped = inferences.stream()
                        .filter(i -> i.getCreatedAt() != null)
                        .collect(Collectors.groupingBy(i -> {
                            LocalDate date = i.getCreatedAt().toLocalDate();
                            LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                            return weekStart.toString();
                        }, TreeMap::new, Collectors.toList()));
                break;
            case "MONTH":
                grouped = inferences.stream()
                        .filter(i -> i.getCreatedAt() != null)
                        .collect(Collectors.groupingBy(i -> {
                            LocalDate date = i.getCreatedAt().toLocalDate();
                            return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
                        }, TreeMap::new, Collectors.toList()));
                break;
            default: // DAY
                grouped = inferences.stream()
                        .filter(i -> i.getCreatedAt() != null)
                        .collect(Collectors.groupingBy(
                                i -> i.getCreatedAt().toLocalDate().toString(),
                                TreeMap::new, Collectors.toList()));
                break;
        }

        List<TrendStatisticsVO> result = new ArrayList<>();
        for (Map.Entry<String, List<Inference>> entry : grouped.entrySet()) {
            TrendStatisticsVO vo = new TrendStatisticsVO();
            vo.setDate(entry.getKey());
            List<Inference> list = entry.getValue();
            vo.setDiseaseCount((int) list.stream().filter(i -> "DISEASE".equals(i.getPipeline())).count());
            vo.setPestCount((int) list.stream().filter(i -> "PEST".equals(i.getPipeline())).count());
            vo.setTotal(list.size());
            result.add(vo);
        }
        return result;
    }

    @Override
    public void exportData(LocalDate startDate, LocalDate endDate, String gridId, String pestType, HttpServletResponse response) {
        // 构建查询条件
        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            wrapper.ge(Inference::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(Inference::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }
        if (pestType != null && !pestType.isEmpty()) {
            wrapper.eq(Inference::getPestName, pestType);
        }

        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        // 如果需要按 gridId 筛选，过滤 inference
        if (gridId != null && !gridId.isEmpty()) {
            Set<String> reportIds = inferences.stream()
                    .map(Inference::getReportId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!reportIds.isEmpty()) {
                Map<String, String> reportGridMap = getReportGridMap(reportIds);
                inferences = inferences.stream()
                        .filter(i -> i.getReportId() != null && gridId.equals(reportGridMap.get(i.getReportId())))
                        .collect(Collectors.toList());
            } else {
                inferences = Collections.emptyList();
            }
        }

        // 检查数据量限制
        if (inferences.size() > 5000) {
            throw new BusinessException(40070, "导出数据量超过5000条，请缩小筛选范围");
        }

        // 获取关联数据
        Set<String> reportIds = inferences.stream()
                .map(Inference::getReportId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> reportGridMap = reportIds.isEmpty() ? Collections.emptyMap() : getReportGridMap(reportIds);
        Set<String> gridIds = new HashSet<>(reportGridMap.values());
        Map<String, String> gridLabelMap = gridIds.isEmpty() ? Collections.emptyMap() : getGridLabelMap(gridIds);

        // 构建导出数据
        List<Map<Integer, String>> exportData = new ArrayList<>();
        for (Inference inf : inferences) {
            Map<Integer, String> row = new LinkedHashMap<>();
            String gridLabel = "";
            if (inf.getReportId() != null && reportGridMap.containsKey(inf.getReportId())) {
                String gId = reportGridMap.get(inf.getReportId());
                gridLabel = gridLabelMap.getOrDefault(gId, gId);
            }
            row.put(0, inf.getId());
            row.put(1, gridLabel);
            row.put(2, inf.getPipeline() != null ? ("DISEASE".equals(inf.getPipeline()) ? "病害" : "虫害") : "");
            row.put(3, inf.getPestName() != null ? inf.getPestName() : "");
            row.put(4, inf.getConfidence() != null ? inf.getConfidence().toPlainString() : "");
            row.put(5, inf.getCreatedAt() != null ? inf.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            exportData.add(row);
        }

        // 写入 Excel
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("统计数据导出", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            List<List<String>> head = Arrays.asList(
                    Collections.singletonList("识别ID"),
                    Collections.singletonList("网格区域"),
                    Collections.singletonList("类型"),
                    Collections.singletonList("病虫害名称"),
                    Collections.singletonList("置信度"),
                    Collections.singletonList("识别时间")
            );

            EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .sheet("统计数据")
                    .doWrite(exportData);
        } catch (IOException e) {
            throw new BusinessException(500, "导出失败: " + e.getMessage());
        }
    }

    /**
     * 构建网格热力图数据: Inference -> Report -> Grid
     */
    private List<StatisticsOverviewVO.GridHeatmap> buildGridHeatmap(List<Inference> inferences) {
        Set<String> reportIds = inferences.stream()
                .map(Inference::getReportId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (reportIds.isEmpty()) return Collections.emptyList();

        Map<String, String> reportGridMap = getReportGridMap(reportIds);
        Set<String> gridIds = new HashSet<>(reportGridMap.values());
        Map<String, String> gridLabelMap = getGridLabelMap(gridIds);

        // 按 gridId 统计
        Map<String, Long> gridCount = inferences.stream()
                .filter(i -> i.getReportId() != null && reportGridMap.containsKey(i.getReportId()))
                .collect(Collectors.groupingBy(i -> reportGridMap.get(i.getReportId()), Collectors.counting()));

        long maxCount = gridCount.values().stream().max(Long::compareTo).orElse(1L);

        List<StatisticsOverviewVO.GridHeatmap> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : gridCount.entrySet()) {
            StatisticsOverviewVO.GridHeatmap hm = new StatisticsOverviewVO.GridHeatmap();
            hm.setGridId(entry.getKey());
            hm.setGridLabel(gridLabelMap.getOrDefault(entry.getKey(), entry.getKey()));
            hm.setScore(BigDecimal.valueOf(entry.getValue())
                    .divide(BigDecimal.valueOf(maxCount), 2, RoundingMode.HALF_UP)
                    .doubleValue());
            result.add(hm);
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result;
    }

    /**
     * 获取 reportId -> gridId 映射
     */
    private Map<String, String> getReportGridMap(Set<String> reportIds) {
        if (reportIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Report::getId, reportIds);
        return reportMapper.selectList(wrapper).stream()
                .filter(r -> r.getGridId() != null)
                .collect(Collectors.toMap(Report::getId, Report::getGridId, (a, b) -> a));
    }

    /**
     * 获取 gridId -> gridLabel 映射
     */
    private Map<String, String> getGridLabelMap(Set<String> gridIds) {
        if (gridIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<Grid> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Grid::getId, gridIds);
        return gridMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(Grid::getId, Grid::getLabel, (a, b) -> a));
    }
}
