package com.agriculture.modules.statistics.service.impl;

import com.agriculture.modules.report.entity.Report;
import com.agriculture.modules.report.mapper.ReportMapper;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.grid.mapper.GridMapper;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.greenhouse.mapper.GreenhouseMapper;
import com.agriculture.modules.inspection.mapper.InspectionLogMapper;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.statistics.service.StatisticService;
import com.agriculture.modules.grid.vo.GridStatisticsVO;
import com.agriculture.modules.statistics.vo.StatisticsOverviewVO;
import com.agriculture.modules.statistics.vo.TrendStatisticsVO;
import com.agriculture.common.websocket.WebSocketService;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private com.agriculture.modules.report.mapper.ReportMapper reportMapper;

    @Resource
    private InferenceMapper inferenceMapper;

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Resource
    private com.agriculture.modules.grid.mapper.GridMapper gridMapper;

    @Resource
    private GreenhouseMapper greenhouseMapper;

    @Resource
    private InspectionLogMapper inspectionLogMapper;

    @Resource
    private WebSocketService webSocketService;

    // ==================== JSON 解析辅助 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDetections(Inference inference) {
        if (inference == null || !StringUtils.hasText(inference.getDetections())) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(inference.getDetections(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 将一条 Inference（含 JSON detections 数组）展开为多条扁平记录
     */
    private List<FlatDetection> flattenDetections(List<Inference> inferences) {
        List<FlatDetection> result = new ArrayList<>();
        for (Inference inf : inferences) {
            List<Map<String, Object>> dets = parseDetections(inf);
            for (Map<String, Object> d : dets) {
                FlatDetection fd = new FlatDetection();
                fd.inferenceId = inf.getId();
                fd.reportId = inf.getReportId();
                fd.createdAt = inf.getCreatedAt();
                fd.annotatedImageUrl = inf.getAnnotatedImageUrl();
                fd.pipeline = (String) d.get("pipeline");
                fd.classId = d.get("class_id") != null ? ((Number) d.get("class_id")).intValue() : null;
                fd.className = (String) d.get("class_name");
                fd.nameCn = (String) d.get("name_cn");
                Object conf = d.get("confidence");
                fd.confidence = conf != null ? new BigDecimal(conf.toString()) : null;
                result.add(fd);
            }
        }
        return result;
    }

    private static class FlatDetection {
        String inferenceId;
        String reportId;
        LocalDateTime createdAt;
        String annotatedImageUrl;
        String pipeline;
        Integer classId;
        String className;
        String nameCn;
        BigDecimal confidence;
    }

    // ==================== 业务方法 ====================

    @Override
    public StatisticsOverviewVO getOverview(Integer days, String companyId) {
        if (days == null) days = 7;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        boolean hasCompany = StringUtils.hasText(companyId);

        StatisticsOverviewVO vo = new StatisticsOverviewVO();

        // 总上报数
        LambdaQueryWrapper<Report> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.ge(Report::getCreatedAt, startTime).le(Report::getCreatedAt, now);
        vo.setTotalReports(reportMapper.selectCount(reportWrapper).intValue());

        // 今日上报数
        LambdaQueryWrapper<Report> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(Report::getCreatedAt, todayStart).le(Report::getCreatedAt, now);
        vo.setTodayReports(reportMapper.selectCount(todayWrapper).intValue());

        // 待审核数
        LambdaQueryWrapper<Report> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.in(Report::getStatus, "PENDING", "PENDING_RECOGNITION");
        vo.setPendingAudit(reportMapper.selectCount(pendingWrapper).intValue());

        // 已处理数
        LambdaQueryWrapper<Report> processedWrapper = new LambdaQueryWrapper<>();
        processedWrapper.eq(Report::getStatus, "AUDITED");
        vo.setProcessed(reportMapper.selectCount(processedWrapper).intValue());

        // 高风险告警
        LambdaQueryWrapper<WorkOrder> alertWrapper = new LambdaQueryWrapper<>();
        alertWrapper.eq(WorkOrder::getSeverity, "CRITICAL")
                    .ne(WorkOrder::getStatus, "DONE")
                    .eq(hasCompany, WorkOrder::getCompanyId, companyId);
        vo.setHighRiskAlerts(workOrderMapper.selectCount(alertWrapper).intValue());

        // ========== 从 work_order 表查询数据（报警工单） ==========

        // 查询时间窗口内的工单（排除 IGNORED 状态）
        LambdaQueryWrapper<WorkOrder> woWrapper = new LambdaQueryWrapper<>();
        woWrapper.ge(WorkOrder::getCreatedAt, startTime)
                 .le(WorkOrder::getCreatedAt, now)
                 .ne(WorkOrder::getStatus, "IGNORED")
                 .eq(hasCompany, WorkOrder::getCompanyId, companyId);
        List<WorkOrder> allOrders = workOrderMapper.selectList(woWrapper);

        // 今日工单（用于病害/虫害分布环图）
        LambdaQueryWrapper<WorkOrder> todayWoWrapper = new LambdaQueryWrapper<>();
        todayWoWrapper.ge(WorkOrder::getCreatedAt, todayStart)
                      .le(WorkOrder::getCreatedAt, now)
                      .ne(WorkOrder::getStatus, "IGNORED")
                      .eq(hasCompany, WorkOrder::getCompanyId, companyId);
        List<WorkOrder> todayOrders = workOrderMapper.selectList(todayWoWrapper);

        // 类型分布 (按病虫害名称)
        Map<String, Long> typeCount = allOrders.stream()
                .filter(w -> w.getPestName() != null)
                .collect(Collectors.groupingBy(WorkOrder::getPestName, Collectors.counting()));
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

        // 每日趋势 (按工单 created_at 分组)
        Map<LocalDate, List<WorkOrder>> dailyGroups = allOrders.stream()
                .filter(w -> w.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        w -> w.getCreatedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));
        List<StatisticsOverviewVO.DailyTrend> dailyTrend = new ArrayList<>();
        for (Map.Entry<LocalDate, List<WorkOrder>> entry : dailyGroups.entrySet()) {
            StatisticsOverviewVO.DailyTrend trend = new StatisticsOverviewVO.DailyTrend();
            trend.setDate(entry.getKey().toString());
            int disease = (int) entry.getValue().stream().filter(w -> "disease".equals(w.getType())).count();
            int pest = (int) entry.getValue().stream().filter(w -> "pest".equals(w.getType())).count();
            trend.setDiseaseCount(disease);
            trend.setPestCount(pest);
            trend.setCount(entry.getValue().size());
            dailyTrend.add(trend);
        }
        vo.setDailyTrend(dailyTrend);

        // Top5 病虫害
        Map<String, Long> nameCount = allOrders.stream()
                .filter(w -> w.getPestName() != null)
                .collect(Collectors.groupingBy(WorkOrder::getPestName, Collectors.counting()));
        List<StatisticsOverviewVO.TopPest> top5Pests = nameCount.entrySet().stream()
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

        // 病害分布 (type=disease, 今日工单, 按名称分组, top 10)
        Map<String, Long> diseaseCount = todayOrders.stream()
                .filter(w -> "disease".equals(w.getType()) && w.getPestName() != null)
                .collect(Collectors.groupingBy(WorkOrder::getPestName, Collectors.counting()));
        List<StatisticsOverviewVO.TypeDistribution> diseaseDistribution = diseaseCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    StatisticsOverviewVO.TypeDistribution td = new StatisticsOverviewVO.TypeDistribution();
                    td.setName(entry.getKey());
                    td.setValue(entry.getValue().intValue());
                    return td;
                })
                .collect(Collectors.toList());
        vo.setDiseaseDistribution(diseaseDistribution);

        // 虫害分布 (type=pest, 今日工单, 按名称分组, top 10)
        Map<String, Long> pestCount = todayOrders.stream()
                .filter(w -> "pest".equals(w.getType()) && w.getPestName() != null)
                .collect(Collectors.groupingBy(WorkOrder::getPestName, Collectors.counting()));
        List<StatisticsOverviewVO.TypeDistribution> pestDistribution = pestCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    StatisticsOverviewVO.TypeDistribution td = new StatisticsOverviewVO.TypeDistribution();
                    td.setName(entry.getKey());
                    td.setValue(entry.getValue().intValue());
                    return td;
                })
                .collect(Collectors.toList());
        vo.setPestDistribution(pestDistribution);

        // Top5 病害 (type=disease)
        List<StatisticsOverviewVO.TopPest> top5Diseases = diseaseCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    StatisticsOverviewVO.TopPest tp = new StatisticsOverviewVO.TopPest();
                    tp.setName(entry.getKey());
                    tp.setCount(entry.getValue().intValue());
                    return tp;
                })
                .collect(Collectors.toList());
        vo.setTop5Diseases(top5Diseases);

        // 网格热力图 (基于工单)
        List<StatisticsOverviewVO.GridHeatmap> gridHeatmap = buildGridHeatmapFromOrders(allOrders);
        vo.setGridHeatmap(gridHeatmap);

        // 推送热力图更新到 WebSocket
        try {
            for (StatisticsOverviewVO.GridHeatmap hm : gridHeatmap) {
                Map<String, Object> wsData = new HashMap<>();
                wsData.put("gridId", hm.getGridId());
                wsData.put("gridLabel", hm.getGridLabel());
                wsData.put("score", hm.getScore());
                wsData.put("updatedAt", now.toString());
                webSocketService.sendHeatmapUpdate(wsData);
            }
        } catch (Exception e) {
            // 推送失败不影响主流程
        }

        return vo;
    }

    @Override
    public List<GridStatisticsVO> getGridStatistics(Integer days, String companyId) {
        if (days == null) days = 7;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days);

        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Inference::getCreatedAt, startTime).le(Inference::getCreatedAt, now);
        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        // 如果有 companyId，过滤只属于该企业的推理记录（通过 report→grid→greenhouse→company 链路）
        if (StringUtils.hasText(companyId)) {
            Set<String> rIds = inferences.stream()
                    .map(Inference::getReportId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!rIds.isEmpty()) {
                Set<String> companyReportIds = getReportIdsByCompany(rIds, companyId);
                inferences = inferences.stream()
                        .filter(i -> i.getReportId() != null && companyReportIds.contains(i.getReportId()))
                        .collect(Collectors.toList());
            } else {
                inferences = Collections.emptyList();
            }
        }

        if (inferences.isEmpty()) {
            return Collections.emptyList();
        }

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
            List<FlatDetection> flat = flattenDetections(entry.getValue());

            GridStatisticsVO vo = new GridStatisticsVO();
            vo.setGridId(gridId);
            vo.setGridLabel(gridLabelMap.getOrDefault(gridId, gridId));
            vo.setTotalDetections(flat.size());
            vo.setDiseaseCount((int) flat.stream().filter(f -> "DISEASE".equals(f.pipeline)).count());
            vo.setPestCount((int) flat.stream().filter(f -> "PEST".equals(f.pipeline)).count());

            // 平均置信度
            BigDecimal totalConf = flat.stream()
                    .filter(f -> f.confidence != null)
                    .map(f -> f.confidence)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!flat.isEmpty()) {
                vo.setAvgConfidence(totalConf.divide(BigDecimal.valueOf(flat.size()), 4, RoundingMode.HALF_UP));
            } else {
                vo.setAvgConfidence(BigDecimal.ZERO);
            }

            // 最高频病虫害
            String topPest = flat.stream()
                    .filter(f -> f.nameCn != null)
                    .collect(Collectors.groupingBy(f -> f.nameCn, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            vo.setTopPest(topPest);

            result.add(vo);
        }

        result.sort((a, b) -> Integer.compare(b.getTotalDetections(), a.getTotalDetections()));
        return result;
    }

    @Override
    public List<TrendStatisticsVO> getTrend(Integer days, String granularity, String companyId) {
        if (days == null) days = 30;
        if (granularity == null) granularity = "DAY";

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days);

        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Inference::getCreatedAt, startTime).le(Inference::getCreatedAt, now);
        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        // 如果有 companyId，过滤只属于该企业的推理记录（通过 report→grid→greenhouse→company 链路）
        if (StringUtils.hasText(companyId)) {
            Set<String> rIds = inferences.stream()
                    .map(Inference::getReportId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!rIds.isEmpty()) {
                Set<String> companyReportIds = getReportIdsByCompany(rIds, companyId);
                inferences = inferences.stream()
                        .filter(i -> i.getReportId() != null && companyReportIds.contains(i.getReportId()))
                        .collect(Collectors.toList());
            } else {
                inferences = Collections.emptyList();
            }
        }

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
            default:
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
            List<FlatDetection> flat = flattenDetections(entry.getValue());
            vo.setDiseaseCount((int) flat.stream().filter(f -> "DISEASE".equals(f.pipeline)).count());
            vo.setPestCount((int) flat.stream().filter(f -> "PEST".equals(f.pipeline)).count());
            vo.setTotal(flat.size());
            result.add(vo);
        }
        return result;
    }

    @Override
    public void exportData(LocalDate startDate, LocalDate endDate, String gridId, String pestType, HttpServletResponse response) {
        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            wrapper.ge(Inference::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(Inference::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }

        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        // 展开为扁平检测列表
        List<FlatDetection> flat = flattenDetections(inferences);

        // 按 pestType 筛选（name_cn 或 class_name）
        if (StringUtils.hasText(pestType)) {
            flat = flat.stream()
                    .filter(f -> pestType.equals(f.nameCn) || pestType.equals(f.className))
                    .collect(Collectors.toList());
        }

        // 按 gridId 筛选
        if (StringUtils.hasText(gridId)) {
            Set<String> reportIds = flat.stream()
                    .map(f -> f.reportId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!reportIds.isEmpty()) {
                Map<String, String> reportGridMap = getReportGridMap(reportIds);
                flat = flat.stream()
                        .filter(f -> f.reportId != null && gridId.equals(reportGridMap.get(f.reportId)))
                        .collect(Collectors.toList());
            } else {
                flat = Collections.emptyList();
            }
        }

        if (flat.size() > 5000) {
            throw new BusinessException(40070, "导出数据量超过5000条，请缩小筛选范围");
        }

        // 获取关联数据
        Set<String> reportIds = flat.stream()
                .map(f -> f.reportId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> reportGridMap = reportIds.isEmpty() ? Collections.emptyMap() : getReportGridMap(reportIds);
        Set<String> gridIds = new HashSet<>(reportGridMap.values());
        Map<String, String> gridLabelMap = gridIds.isEmpty() ? Collections.emptyMap() : getGridLabelMap(gridIds);

        // 构建导出数据
        List<Map<Integer, String>> exportData = new ArrayList<>();
        for (FlatDetection f : flat) {
            Map<Integer, String> row = new LinkedHashMap<>();
            String gridLabel = "";
            if (f.reportId != null && reportGridMap.containsKey(f.reportId)) {
                String gId = reportGridMap.get(f.reportId);
                gridLabel = gridLabelMap.getOrDefault(gId, gId);
            }
            row.put(0, f.inferenceId);
            row.put(1, gridLabel);
            row.put(2, f.pipeline != null ? ("DISEASE".equals(f.pipeline) ? "病害" : "虫害") : "");
            row.put(3, f.nameCn != null ? f.nameCn : "");
            row.put(4, f.confidence != null ? f.confidence.toPlainString() : "");
            row.put(5, f.createdAt != null ? f.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            exportData.add(row);
        }

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
     * 构建网格热力图数据（基于 inference）
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

        // 按 gridId 统计（每条 inference 算一次记录）
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
     * 构建网格热力图数据（基于工单）
     */
    private List<StatisticsOverviewVO.GridHeatmap> buildGridHeatmapFromOrders(List<WorkOrder> orders) {
        // 按 gridLabel 统计工单数
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

    private Map<String, String> getReportGridMap(Set<String> reportIds) {
        if (reportIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Report::getId, reportIds);
        return reportMapper.selectList(wrapper).stream()
                .filter(r -> r.getGridId() != null)
                .collect(Collectors.toMap(Report::getId, Report::getGridId, (a, b) -> a));
    }

    private Map<String, String> getGridLabelMap(Set<String> gridIds) {
        if (gridIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<Grid> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Grid::getId, gridIds);
        return gridMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(Grid::getId, Grid::getLabel, (a, b) -> a));
    }

    /**
     * 通过 report→grid→greenhouse→company 链路过滤，返回属于指定企业的 reportId 集合
     */
    private Set<String> getReportIdsByCompany(Set<String> reportIds, String companyId) {
        if (reportIds.isEmpty()) return Collections.emptySet();

        // 1. report → gridId
        LambdaQueryWrapper<Report> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.in(Report::getId, reportIds);
        List<Report> reports = reportMapper.selectList(reportWrapper);
        Set<String> gridIds = reports.stream()
                .map(Report::getGridId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (gridIds.isEmpty()) return Collections.emptySet();

        // 2. grid → greenhouseId
        LambdaQueryWrapper<Grid> gridWrapper = new LambdaQueryWrapper<>();
        gridWrapper.in(Grid::getId, gridIds);
        Set<String> greenhouseIds = gridMapper.selectList(gridWrapper).stream()
                .map(Grid::getGreenhouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (greenhouseIds.isEmpty()) return Collections.emptySet();

        // 3. greenhouse → filter by companyId
        LambdaQueryWrapper<com.agriculture.modules.greenhouse.entity.Greenhouse> ghWrapper = new LambdaQueryWrapper<>();
        ghWrapper.in(com.agriculture.modules.greenhouse.entity.Greenhouse::getId, greenhouseIds);
        Set<String> companyGreenhouseIds = greenhouseMapper.selectList(ghWrapper).stream()
                .filter(gh -> companyId.equals(gh.getCompanyId()))
                .map(com.agriculture.modules.greenhouse.entity.Greenhouse::getId)
                .collect(Collectors.toSet());

        // 4. 反向筛选：grid.greenhouseId ∈ companyGreenhouseIds → reportId
        LambdaQueryWrapper<Grid> gridFilter = new LambdaQueryWrapper<>();
        gridFilter.in(Grid::getId, gridIds);
        Set<String> companyGridIds = gridMapper.selectList(gridFilter).stream()
                .filter(g -> g.getGreenhouseId() != null && companyGreenhouseIds.contains(g.getGreenhouseId()))
                .map(Grid::getId)
                .collect(Collectors.toSet());

        return reports.stream()
                .filter(r -> r.getGridId() != null && companyGridIds.contains(r.getGridId()))
                .map(Report::getId)
                .collect(Collectors.toSet());
    }
}
