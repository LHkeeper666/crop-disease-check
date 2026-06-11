package com.agriculture.service.impl;

import com.agriculture.dao.mapper.*;
import com.agriculture.dto.AuditDTO;
import com.agriculture.dto.PreventionPlanDTO;
import com.agriculture.entity.*;
import com.agriculture.exception.BusinessException;
import com.agriculture.service.InferenceService;
import com.agriculture.vo.PendingAuditVO;
import com.agriculture.vo.PendingReviewVO;
import com.agriculture.vo.PreventionPlanVersionVO;
import com.agriculture.vo.PreventionPlanVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    @Resource
    private ReportMapper reportMapper;

    @Resource
    private GridMapper gridMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PestInfoMapper pestInfoMapper;

    @Resource
    private AuditRecordMapper auditRecordMapper;

    @Resource
    private PreventionPlanMapper preventionPlanMapper;

    @Resource
    private PreventionPlanVersionMapper preventionPlanVersionMapper;

    @Override
    public IPage<PendingReviewVO> listPendingReview(String sortByConfidence, int page, int size) {
        // 查询低置信度的识别结果（is_low_confidence = 1）
        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inference::getIsLowConfidence, (byte) 1);

        // 排序
        if ("asc".equalsIgnoreCase(sortByConfidence)) {
            wrapper.orderByAsc(Inference::getConfidence);
        } else {
            wrapper.orderByDesc(Inference::getConfidence);
        }

        Page<Inference> pageParam = new Page<>(page, size);
        Page<Inference> result = baseMapper.selectPage(pageParam, wrapper);

        Page<PendingReviewVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toPendingReviewVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public IPage<PendingAuditVO> listPendingAudit(int page, int size) {
        // 查询状态为 PENDING 的上报记录，关联识别结果
        LambdaQueryWrapper<Report> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.eq(Report::getStatus, "PENDING")
                     .orderByDesc(Report::getCreatedAt);

        Page<Report> pageParam = new Page<>(page, size);
        Page<Report> result = reportMapper.selectPage(pageParam, reportWrapper);

        Page<PendingAuditVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toPendingAuditVO)
                .collect(Collectors.toList()));
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

        // 驳回时校验 comment
        if ("REJECTED".equals(action)) {
            if (!StringUtils.hasText(dto.getComment())) {
                throw new BusinessException(40050, "驳回原因不能为空");
            }
            if (dto.getComment().length() < 10) {
                throw new BusinessException(40051, "驳回原因不能少于10字");
            }
        }

        // 更新上报状态
        report.setStatus("REJECTED".equals(action) ? "REJECTED" : "AUDITED");
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);

        // 记录审核记录
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

        // 检查是否已有方案
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

        // 保存历史版本
        PreventionPlanVersion version = new PreventionPlanVersion();
        version.setPlanId(plan.getId());
        version.setContent(plan.getContent());
        version.setSuggestTime(plan.getSuggestTime());
        version.setVersion(plan.getVersion());
        version.setCreatedAt(LocalDateTime.now());
        preventionPlanVersionMapper.insert(version);

        // 更新方案
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

        // 查询制定人姓名
        if (StringUtils.hasText(plan.getAuthorId())) {
            SysUser author = sysUserMapper.selectById(plan.getAuthorId());
            if (author != null) {
                vo.setAuthorName(author.getName());
            }
        }

        // 查询历史版本
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
        }).collect(Collectors.toList()));

        return vo;
    }

    // ==================== 私有辅助方法 ====================

    private PendingReviewVO toPendingReviewVO(Inference inference) {
        PendingReviewVO vo = new PendingReviewVO();
        vo.setId(inference.getId());
        vo.setReportId(inference.getReportId());
        vo.setPestName(inference.getPestName());
        vo.setConfidence(inference.getConfidence());
        vo.setCreatedAt(inference.getCreatedAt());

        // 关联查询上报信息
        if (StringUtils.hasText(inference.getReportId())) {
            Report report = reportMapper.selectById(inference.getReportId());
            if (report != null) {
                vo.setFoundAt(report.getFoundAt());
                // 获取图片URL（取第一张）
                if (StringUtils.hasText(report.getImageUrls())) {
                    String[] urls = report.getImageUrls().split(",");
                    vo.setImageUrl(urls[0].trim());
                }
                // 获取上报人姓名
                if (StringUtils.hasText(report.getUserId())) {
                    SysUser reporter = sysUserMapper.selectById(report.getUserId());
                    if (reporter != null) {
                        vo.setReporterName(reporter.getName());
                    }
                }
                // 获取网格标签
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

        // 获取图片URL（取第一张）
        if (StringUtils.hasText(report.getImageUrls())) {
            String[] urls = report.getImageUrls().split(",");
            vo.setImageUrl(urls[0].trim());
        }

        // 获取上报人姓名
        if (StringUtils.hasText(report.getUserId())) {
            SysUser reporter = sysUserMapper.selectById(report.getUserId());
            if (reporter != null) {
                vo.setReporterName(reporter.getName());
            }
        }

        // 获取网格标签
        if (StringUtils.hasText(report.getGridId())) {
            Grid grid = gridMapper.selectById(report.getGridId());
            if (grid != null) {
                vo.setGridLabel(grid.getLabel());
            }
        }

        // 获取识别结果（取置信度最高的一个）
        LambdaQueryWrapper<Inference> inferenceWrapper = new LambdaQueryWrapper<>();
        inferenceWrapper.eq(Inference::getReportId, report.getId())
                        .orderByDesc(Inference::getConfidence)
                        .last("LIMIT 1");
        Inference inference = baseMapper.selectOne(inferenceWrapper);
        if (inference != null) {
            vo.setId(inference.getId());
            vo.setPestName(inference.getPestName());
            vo.setConfidence(inference.getConfidence());
        }

        return vo;
    }
}
