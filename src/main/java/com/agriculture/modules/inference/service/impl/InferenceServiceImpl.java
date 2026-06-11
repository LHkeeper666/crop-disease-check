package com.agriculture.modules.inference.service.impl;

import com.agriculture.modules.pestDiseaseInfo.dto.AuditDTO;
import com.agriculture.modules.pestDiseaseInfo.dto.PreventionPlanDTO;
import com.agriculture.modules.report.entity.Report;
import com.agriculture.modules.report.mapper.ReportMapper;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.grid.mapper.GridMapper;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.PestInfoMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.DiseaseInfoMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.AuditRecordMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.PreventionPlanMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.PreventionPlanVersionMapper;
import com.agriculture.modules.pestDiseaseInfo.entity.PreventionPlan;
import com.agriculture.modules.pestDiseaseInfo.entity.PreventionPlanVersion;
import com.agriculture.modules.pestDiseaseInfo.entity.AuditRecord;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.inference.service.InferenceService;
import com.agriculture.modules.pestDiseaseInfo.vo.PendingAuditVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PendingReviewVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PreventionPlanVersionVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PreventionPlanVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 识别结果表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class InferenceServiceImpl extends ServiceImpl<InferenceMapper, Inference> implements InferenceService {

    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.6");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private ReportMapper reportMapper;

    @Resource
    private GridMapper gridMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PestInfoMapper pestInfoMapper;

    @Resource
    private DiseaseInfoMapper diseaseInfoMapper;

    @Resource
    private AuditRecordMapper auditRecordMapper;

    @Resource
    private PreventionPlanMapper preventionPlanMapper;

    @Resource
    private PreventionPlanVersionMapper preventionPlanVersionMapper;

    @Override
    public IPage<PendingReviewVO> listPendingReview(String sortByConfidence, int page, int size) {
        // 全量查询（detections 在 JSON 中，无法用 SQL 直接过滤置信度）
        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        if ("asc".equalsIgnoreCase(sortByConfidence)) {
            wrapper.orderByAsc(Inference::getCreatedAt);
        } else {
            wrapper.orderByDesc(Inference::getCreatedAt);
        }

        Page<Inference> pageParam = new Page<>(page, size);
        Page<Inference> result = baseMapper.selectPage(pageParam, wrapper);

        // 在应用层过滤：保留含有低置信度检测的记录
        List<PendingReviewVO> filtered = new ArrayList<>();
        for (Inference inference : result.getRecords()) {
            List<Map<String, Object>> dets = parseDetections(inference.getDetections());
            boolean hasLow = dets.stream()
                    .anyMatch(d -> {
                        Object conf = d.get("confidence");
                        return conf != null && new BigDecimal(conf.toString()).compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;
                    });
            if (hasLow) {
                filtered.add(toPendingReviewVO(inference, dets));
            }
        }

        Page<PendingReviewVO> voPage = new Page<>(result.getCurrent(), result.getSize(), filtered.size());
        voPage.setRecords(filtered);
        return voPage;
    }

    @Override
    public IPage<PendingAuditVO> listPendingAudit(int page, int size) {
        LambdaQueryWrapper<Report> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.eq(Report::getStatus, "PENDING")
                     .orderByDesc(Report::getCreatedAt);

        Page<Report> pageParam = new Page<>(page, size);
        Page<Report> result = reportMapper.selectPage(pageParam, reportWrapper);

        Page<PendingAuditVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toPendingAuditVO)
                .collect(java.util.stream.Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional
    public void auditReport(String reportId, AuditDTO dto, String auditorId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "上报记录不存在");
        }
        if ("AUDITED".equals(report.getStatus()) || "REJECTED".equals(report.getStatus())) {
            throw new BusinessException(40052, "该记录已被审核");
        }

        String action = dto.getAction();
        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            throw new BusinessException("无效的审核操作: " + action);
        }

        if ("REJECTED".equals(action)) {
            if (!StringUtils.hasText(dto.getComment())) {
                throw new BusinessException(40050, "驳回原因不能为空");
            }
            if (dto.getComment().length() < 10) {
                throw new BusinessException(40051, "驳回原因不能少于10字");
            }
        }

        report.setStatus("REJECTED".equals(action) ? "REJECTED" : "AUDITED");
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);

        AuditRecord record = new AuditRecord();
        record.setReportId(reportId);
        record.setAuditorId(auditorId);
        record.setAuditResult(action);
        record.setComment(dto.getComment());
        record.setAuditedAt(LocalDateTime.now());
        auditRecordMapper.insert(record);
    }

    @Override
    @Transactional
    public void createPreventionPlan(String reportId, PreventionPlanDTO dto, String authorId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "上报记录不存在");
        }
        if (!"AUDITED".equals(report.getStatus())) {
            throw new BusinessException("仅已审核通过的记录可制定防治方案");
        }

        LambdaQueryWrapper<PreventionPlan> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(PreventionPlan::getReportId, reportId);
        PreventionPlan existPlan = preventionPlanMapper.selectOne(existWrapper);
        if (existPlan != null) {
            throw new BusinessException("该上报已有防治方案，请使用修改接口");
        }

        PreventionPlan plan = new PreventionPlan();
        plan.setReportId(reportId);
        plan.setContent(dto.getContent());
        plan.setSuggestTime(dto.getSuggestTime());
        plan.setAuthorId(authorId);
        plan.setVersion(1);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        preventionPlanMapper.insert(plan);
    }

    @Override
    @Transactional
    public void updatePreventionPlan(String reportId, PreventionPlanDTO dto, String authorId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "上报记录不存在");
        }

        LambdaQueryWrapper<PreventionPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PreventionPlan::getReportId, reportId);
        PreventionPlan plan = preventionPlanMapper.selectOne(wrapper);
        if (plan == null) {
            throw new BusinessException(404, "防治方案不存在，请先制定方案");
        }

        PreventionPlanVersion version = new PreventionPlanVersion();
        version.setPlanId(plan.getId());
        version.setContent(plan.getContent());
        version.setSuggestTime(plan.getSuggestTime());
        version.setVersion(plan.getVersion());
        version.setCreatedAt(LocalDateTime.now());
        preventionPlanVersionMapper.insert(version);

        plan.setContent(dto.getContent());
        plan.setSuggestTime(dto.getSuggestTime());
        plan.setVersion(plan.getVersion() + 1);
        plan.setUpdatedAt(LocalDateTime.now());
        preventionPlanMapper.updateById(plan);
    }

    @Override
    public PreventionPlanVO getPreventionPlan(String reportId) {
        LambdaQueryWrapper<PreventionPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PreventionPlan::getReportId, reportId);
        PreventionPlan plan = preventionPlanMapper.selectOne(wrapper);
        if (plan == null) {
            return null;
        }

        PreventionPlanVO vo = new PreventionPlanVO();
        vo.setId(plan.getId());
        vo.setContent(plan.getContent());
        vo.setSuggestTime(plan.getSuggestTime());
        vo.setVersion(plan.getVersion());
        vo.setCreatedAt(plan.getCreatedAt());

        if (StringUtils.hasText(plan.getAuthorId())) {
            SysUser author = sysUserMapper.selectById(plan.getAuthorId());
            if (author != null) {
                vo.setAuthorName(author.getName());
            }
        }

        LambdaQueryWrapper<PreventionPlanVersion> versionWrapper = new LambdaQueryWrapper<>();
        versionWrapper.eq(PreventionPlanVersion::getPlanId, plan.getId())
                      .orderByDesc(PreventionPlanVersion::getVersion);
        List<PreventionPlanVersion> versions = preventionPlanVersionMapper.selectList(versionWrapper);
        vo.setVersions(versions.stream().map(v -> {
            PreventionPlanVersionVO vvo = new PreventionPlanVersionVO();
            vvo.setId(v.getId());
            vvo.setContent(v.getContent());
            vvo.setSuggestTime(v.getSuggestTime());
            vvo.setVersion(v.getVersion());
            vvo.setCreatedAt(v.getCreatedAt());
            return vvo;
        }).collect(java.util.stream.Collectors.toList()));

        return vo;
    }

    // ==================== 私有辅助方法 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDetections(String detectionsJson) {
        if (!StringUtils.hasText(detectionsJson)) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(detectionsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 从 detections 中提取置信度最高的检测项名称（中文）
     */
    private String extractTopName(List<Map<String, Object>> dets) {
        return dets.stream()
                .max((a, b) -> {
                    BigDecimal ca = new BigDecimal(a.getOrDefault("confidence", 0).toString());
                    BigDecimal cb = new BigDecimal(b.getOrDefault("confidence", 0).toString());
                    return ca.compareTo(cb);
                })
                .map(d -> (String) d.getOrDefault("name_cn", d.getOrDefault("class_name", "")))
                .orElse("");
    }

    /**
     * 从 detections 中提取最高置信度
     */
    private BigDecimal extractTopConfidence(List<Map<String, Object>> dets) {
        return dets.stream()
                .map(d -> new BigDecimal(d.getOrDefault("confidence", 0).toString()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private PendingReviewVO toPendingReviewVO(Inference inference, List<Map<String, Object>> dets) {
        PendingReviewVO vo = new PendingReviewVO();
        vo.setId(inference.getId());
        vo.setReportId(inference.getReportId());
        vo.setPestName(extractTopName(dets));
        vo.setConfidence(extractTopConfidence(dets));
        vo.setCreatedAt(inference.getCreatedAt());

        if (StringUtils.hasText(inference.getReportId())) {
            Report report = reportMapper.selectById(inference.getReportId());
            if (report != null) {
                vo.setFoundAt(report.getFoundAt());
                if (StringUtils.hasText(report.getImageUrls())) {
                    String[] urls = report.getImageUrls().split(",");
                    vo.setImageUrl(urls[0].trim());
                }
                if (StringUtils.hasText(report.getUserId())) {
                    SysUser reporter = sysUserMapper.selectById(report.getUserId());
                    if (reporter != null) {
                        vo.setReporterName(reporter.getName());
                    }
                }
                if (StringUtils.hasText(report.getGridId())) {
                    Grid grid = gridMapper.selectById(report.getGridId());
                    if (grid != null) {
                        vo.setGridLabel(grid.getLabel());
                    }
                }
            }
        }
        return vo;
    }

    private PendingAuditVO toPendingAuditVO(Report report) {
        PendingAuditVO vo = new PendingAuditVO();
        vo.setReportId(report.getId());
        vo.setCropType(report.getCropType());
        vo.setFoundAt(report.getFoundAt());

        if (StringUtils.hasText(report.getImageUrls())) {
            String[] urls = report.getImageUrls().split(",");
            vo.setImageUrl(urls[0].trim());
        }

        if (StringUtils.hasText(report.getUserId())) {
            SysUser reporter = sysUserMapper.selectById(report.getUserId());
            if (reporter != null) {
                vo.setReporterName(reporter.getName());
            }
        }

        if (StringUtils.hasText(report.getGridId())) {
            Grid grid = gridMapper.selectById(report.getGridId());
            if (grid != null) {
                vo.setGridLabel(grid.getLabel());
            }
        }

        // 获取该上报的识别结果（取最新一条）
        LambdaQueryWrapper<Inference> inferenceWrapper = new LambdaQueryWrapper<>();
        inferenceWrapper.eq(Inference::getReportId, report.getId())
                        .orderByDesc(Inference::getCreatedAt)
                        .last("LIMIT 1");
        Inference inference = baseMapper.selectOne(inferenceWrapper);
        if (inference != null) {
            vo.setId(inference.getId());
            List<Map<String, Object>> dets = parseDetections(inference.getDetections());
            vo.setPestName(extractTopName(dets));
            vo.setConfidence(extractTopConfidence(dets));
        }

        return vo;
    }
}
