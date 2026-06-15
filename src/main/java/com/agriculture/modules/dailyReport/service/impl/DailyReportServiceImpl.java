package com.agriculture.modules.dailyReport.service.impl;

import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.agriculture.modules.inspection.mapper.InspectionLogMapper;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.dailyReport.dto.DailyReportGenerateDTO;
import com.agriculture.modules.dailyReport.entity.DailyReport;
import com.agriculture.modules.dailyReport.mapper.DailyReportMapper;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inspection.entity.InspectionLog;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.dailyReport.service.DailyReportService;
import com.agriculture.modules.dailyReport.vo.DailyReportDetailVO;
import com.agriculture.modules.dailyReport.vo.DailyReportSummaryDTO;
import com.agriculture.modules.dailyReport.vo.DailyReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DailyReportServiceImpl extends ServiceImpl<DailyReportMapper, DailyReport> implements DailyReportService {

    @Resource
    private InspectionLogMapper inspectionLogMapper;

    @Resource
    private InferenceMapper inferenceMapper;

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public IPage<DailyReportVO> listReports(LocalDate startDate, LocalDate endDate, int page, int size) {
        LambdaQueryWrapper<DailyReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(startDate != null, DailyReport::getReportDate, startDate)
               .le(endDate != null, DailyReport::getReportDate, endDate)
               .orderByDesc(DailyReport::getReportDate);

        Page<DailyReport> pageParam = new Page<>(page, size);
        Page<DailyReport> result = baseMapper.selectPage(pageParam, wrapper);

        Page<DailyReportVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public DailyReportDetailVO getReportDetail(String id) {
        DailyReport report = baseMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(404, "日报不存在");
        }

        DailyReportDetailVO vo = new DailyReportDetailVO();
        vo.setId(report.getId());
        vo.setReportDate(report.getReportDate());
        vo.setEmailSent(report.getEmailSent());
        vo.setEmailSentAt(report.getEmailSentAt());
        vo.setCreatedAt(report.getCreatedAt());

        // 解析 summaryJson
        if (report.getSummaryJson() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> summaryMap = objectMapper.readValue(report.getSummaryJson(), Map.class);
                vo.setSummaryJson(summaryMap);
            } catch (JsonProcessingException e) {
                vo.setSummaryJson(new HashMap<>());
            }
        }

        vo.setHtmlContent(report.getHtmlContent());
        return vo;
    }

    @Override
    @Transactional
    public String generateReport(DailyReportGenerateDTO dto) {
        LocalDate date = dto.getDate();

        // 如果已存在该日期的日报，删除旧的重新生成
        LambdaQueryWrapper<DailyReport> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(DailyReport::getReportDate, date);
        baseMapper.delete(existWrapper);

        // 聚合统计数据
        DailyReportSummaryDTO summary = aggregateStatistics(date);

        // 生成HTML内容
        String htmlContent = generateHtmlContent(date, summary);

        // 序列化summary为JSON
        String summaryJson;
        try {
            summaryJson = objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            summaryJson = "{}";
        }

        // 创建日报记录
        DailyReport report = new DailyReport();
        report.setId(UUID.randomUUID().toString().replace("-", ""));
        report.setReportDate(date);
        report.setDetections(summary.getTotalDetections());
        report.setDiseaseCount(summary.getDiseaseCount());
        report.setPestCount(summary.getPestCount());
        report.setHandledRate(summary.getWorkorderHandledRate());
        report.setGreenhouseId(dto.getGreenhouseId());
        report.setSummaryJson(summaryJson);
        report.setHtmlContent(htmlContent);
        report.setEmailSent((byte) 0);
        report.setCreatedAt(LocalDateTime.now());
        baseMapper.insert(report);

        return report.getId();
    }

    private DailyReportSummaryDTO aggregateStatistics(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

        DailyReportSummaryDTO summary = new DailyReportSummaryDTO();

        // 统计巡检次数
        LambdaQueryWrapper<InspectionLog> inspectionWrapper = new LambdaQueryWrapper<>();
        inspectionWrapper.ge(InspectionLog::getCreatedAt, dayStart)
                         .le(InspectionLog::getCreatedAt, dayEnd);
        Long totalInspections = inspectionLogMapper.selectCount(inspectionWrapper);
        summary.setTotalInspections(totalInspections.intValue());

        // 统计检测结果
        LambdaQueryWrapper<Inference> inferenceWrapper = new LambdaQueryWrapper<>();
        inferenceWrapper.ge(Inference::getCreatedAt, dayStart)
                        .le(Inference::getCreatedAt, dayEnd);
        List<Inference> inferences = inferenceMapper.selectList(inferenceWrapper);

        // 展开 JSON detections 为扁平列表
        List<Map<String, Object>> allDets = new ArrayList<>();
        for (Inference inf : inferences) {
            allDets.addAll(parseDetections(inf.getDetections()));
        }

        summary.setTotalDetections(allDets.size());

        // 按 pipeline 分类统计
        long diseaseCount = allDets.stream()
                .filter(d -> "DISEASE".equals(d.get("pipeline")))
                .count();
        long pestCount = allDets.stream()
                .filter(d -> "PEST".equals(d.get("pipeline")))
                .count();
        summary.setDiseaseCount((int) diseaseCount);
        summary.setPestCount((int) pestCount);

        // 统计高发病虫害TOP5（按 name_cn）
        Map<String, Long> pestNameCount = allDets.stream()
                .filter(d -> d.get("name_cn") != null)
                .collect(Collectors.groupingBy(d -> (String) d.get("name_cn"), Collectors.counting()));
        List<DailyReportSummaryDTO.TopPestDTO> topPests = pestNameCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    DailyReportSummaryDTO.TopPestDTO dto = new DailyReportSummaryDTO.TopPestDTO();
                    dto.setName(entry.getKey());
                    dto.setCount(entry.getValue().intValue());
                    return dto;
                })
                .collect(Collectors.toList());
        summary.setTopPests(topPests);

        // 统计工单处理率
        LambdaQueryWrapper<WorkOrder> workOrderWrapper = new LambdaQueryWrapper<>();
        workOrderWrapper.ge(WorkOrder::getCreatedAt, dayStart)
                        .le(WorkOrder::getCreatedAt, dayEnd);
        List<WorkOrder> workOrders = workOrderMapper.selectList(workOrderWrapper);

        if (workOrders.isEmpty()) {
            summary.setWorkorderHandledRate(BigDecimal.ZERO);
        } else {
            long doneCount = workOrders.stream()
                    .filter(wo -> "DONE".equals(wo.getStatus()))
                    .count();
            BigDecimal rate = BigDecimal.valueOf(doneCount)
                    .divide(BigDecimal.valueOf(workOrders.size()), 2, RoundingMode.HALF_UP);
            summary.setWorkorderHandledRate(rate);
        }

        // 统计高风险网格TOP5（按检测数量统计）
        // 需要通过 Inference -> Report -> Grid 关联查询
        // 这里简化处理，使用巡检日志中的数据
        LambdaQueryWrapper<InspectionLog> gridInspectionWrapper = new LambdaQueryWrapper<>();
        gridInspectionWrapper.ge(InspectionLog::getCreatedAt, dayStart)
                             .le(InspectionLog::getCreatedAt, dayEnd)
                             .isNotNull(InspectionLog::getCameraId);
        List<InspectionLog> inspectionLogs = inspectionLogMapper.selectList(gridInspectionWrapper);

        // 按cameraId分组统计
        Map<String, Long> cameraCount = inspectionLogs.stream()
                .filter(il -> il.getCameraId() != null)
                .collect(Collectors.groupingBy(InspectionLog::getCameraId, Collectors.counting()));

        List<DailyReportSummaryDTO.TopGridDTO> topGrids = cameraCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    DailyReportSummaryDTO.TopGridDTO dto = new DailyReportSummaryDTO.TopGridDTO();
                    dto.setGridLabel(entry.getKey()); // 使用cameraId作为gridLabel
                    dto.setCount(entry.getValue().intValue());
                    return dto;
                })
                .collect(Collectors.toList());
        summary.setTopGrids(topGrids);

        return summary;
    }

    private String generateHtmlContent(LocalDate date, DailyReportSummaryDTO summary) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("body{font-family:’Microsoft YaHei’,sans-serif;margin:20px;color:#333}");
        html.append("h1{color:#1a5276;border-bottom:2px solid #2e86c1;padding-bottom:10px}");
        html.append("h2{color:#2e86c1;margin-top:30px}");
        html.append("table{border-collapse:collapse;width:100%;margin:15px 0}");
        html.append("th,td{border:1px solid #ddd;padding:10px;text-align:left}");
        html.append("th{background-color:#2e86c1;color:white}");
        html.append("tr:nth-child(even){background-color:#f2f2f2}");
        html.append(".stat-box{display:inline-block;width:22%;margin:1%;padding:15px;background:#f8f9fa;border-radius:8px;text-align:center}");
        html.append(".stat-value{font-size:24px;font-weight:bold;color:#2e86c1}");
        html.append(".stat-label{color:#666;margin-top:5px}");
        html.append(".footer{margin-top:40px;padding-top:20px;border-top:1px solid #ddd;color:#888;font-size:12px}");
        html.append("</style></head><body>");

        // 标题
        html.append("<h1>智慧农业病虫害监测系统 - 日度报告</h1>");
        html.append("<p><strong>报告日期：</strong>").append(date).append("</p>");

        // 统计概览
        html.append("<h2>一、统计概览</h2>");
        html.append("<div>");
        html.append("<div class=\"stat-box\"><div class=\"stat-value\">").append(summary.getTotalInspections()).append("</div><div class=\"stat-label\">总巡检次数</div></div>");
        html.append("<div class=\"stat-box\"><div class=\"stat-value\">").append(summary.getTotalDetections()).append("</div><div class=\"stat-label\">总检测次数</div></div>");
        html.append("<div class=\"stat-box\"><div class=\"stat-value\">").append(summary.getDiseaseCount()).append("</div><div class=\"stat-label\">病害数量</div></div>");
        html.append("<div class=\"stat-box\"><div class=\"stat-value\">").append(summary.getPestCount()).append("</div><div class=\"stat-label\">虫害数量</div></div>");
        html.append("</div>");

        // 工单处理概况
        html.append("<h2>二、工单处理概况</h2>");
        html.append("<p><strong>工单处理率：</strong>");
        if (summary.getWorkorderHandledRate() != null) {
            html.append(summary.getWorkorderHandledRate().multiply(BigDecimal.valueOf(100)).intValue()).append("%");
        } else {
            html.append("0%");
        }
        html.append("</p>");

        // 高发病虫害TOP5
        html.append("<h2>三、高发病虫害 TOP5</h2>");
        if (summary.getTopPests() != null && !summary.getTopPests().isEmpty()) {
            html.append("<table><tr><th>排名</th><th>病虫害名称</th><th>检出次数</th></tr>");
            for (int i = 0; i < summary.getTopPests().size(); i++) {
                DailyReportSummaryDTO.TopPestDTO pest = summary.getTopPests().get(i);
                html.append("<tr><td>").append(i + 1).append("</td><td>").append(pest.getName()).append("</td><td>").append(pest.getCount()).append("</td></tr>");
            }
            html.append("</table>");
        } else {
            html.append("<p>当日无病虫害检出记录</p>");
        }

        // 高风险网格TOP5
        html.append("<h2>四、高风险区域 TOP5</h2>");
        if (summary.getTopGrids() != null && !summary.getTopGrids().isEmpty()) {
            html.append("<table><tr><th>排名</th><th>区域/摄像头</th><th>巡检次数</th></tr>");
            for (int i = 0; i < summary.getTopGrids().size(); i++) {
                DailyReportSummaryDTO.TopGridDTO grid = summary.getTopGrids().get(i);
                html.append("<tr><td>").append(i + 1).append("</td><td>").append(grid.getGridLabel()).append("</td><td>").append(grid.getCount()).append("</td></tr>");
            }
            html.append("</table>");
        } else {
            html.append("<p>当日无巡检记录</p>");
        }

        // 页脚
        html.append("<div class=\"footer\">");
        html.append("<p>本报告由智慧农业病虫害监测系统自动生成</p>");
        html.append("<p>生成时间：").append(LocalDateTime.now()).append("</p>");
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDetections(String detectionsJson) {
        if (!StringUtils.hasText(detectionsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(detectionsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private DailyReportVO toVO(DailyReport report) {
        DailyReportVO vo = new DailyReportVO();
        vo.setId(report.getId());
        vo.setReportDate(report.getReportDate());
        vo.setEmailSent(report.getEmailSent());
        vo.setEmailSentAt(report.getEmailSentAt());
        vo.setCreatedAt(report.getCreatedAt());

        // 解析summaryJson
        if (report.getSummaryJson() != null) {
            try {
                DailyReportSummaryDTO summary = objectMapper.readValue(report.getSummaryJson(), DailyReportSummaryDTO.class);
                vo.setSummary(summary);
            } catch (JsonProcessingException e) {
                vo.setSummary(new DailyReportSummaryDTO());
            }
        }
        return vo;
    }
}
