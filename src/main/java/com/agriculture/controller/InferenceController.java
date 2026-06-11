package com.agriculture.controller;

import com.agriculture.dto.AuditDTO;
import com.agriculture.dto.PreventionPlanDTO;
import com.agriculture.service.InferenceService;
import com.agriculture.vo.PendingAuditVO;
import com.agriculture.vo.PendingReviewVO;
import com.agriculture.vo.PreventionPlanVO;
import com.agriculture.vo.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * <p>
 * 病虫害识别 前端控制器
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/inference")
public class InferenceController {

    @Resource
    private InferenceService inferenceService;

    /**
     * 7.1 待复核列表（专家）
     * GET /inference/pending-review
     */
    @GetMapping("/pending-review")
    public Result<IPage<PendingReviewVO>> listPendingReview(
            @RequestParam(required = false) String sortByConfidence,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(inferenceService.listPendingReview(sortByConfidence, page, size));
    }

    /**
     * 7.2 待审核列表（专家）
     * GET /inference/pending-audit
     */
    @GetMapping("/pending-audit")
    public Result<IPage<PendingAuditVO>> listPendingAudit(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(inferenceService.listPendingAudit(page, size));
    }

    /**
     * 7.3 审核上报
     * POST /inference/{reportId}/audit
     */
    @PostMapping("/{reportId}/audit")
    public Result<Void> auditReport(
            @PathVariable String reportId,
            @Valid @RequestBody AuditDTO dto) {
        // TODO: 从 SecurityContext 获取当前用户ID，暂时使用默认值
        String auditorId = "system";
        inferenceService.auditReport(reportId, dto, auditorId);
        return Result.success("审核成功", null);
    }

    /**
     * 7.4 制定防治方案
     * POST /inference/{reportId}/prevention-plan
     */
    @PostMapping("/{reportId}/prevention-plan")
    public Result<Void> createPreventionPlan(
            @PathVariable String reportId,
            @Valid @RequestBody PreventionPlanDTO dto) {
        // TODO: 从 SecurityContext 获取当前用户ID，暂时使用默认值
        String authorId = "system";
        inferenceService.createPreventionPlan(reportId, dto, authorId);
        return Result.success("防治方案制定成功", null);
    }

    /**
     * 7.5 修改防治方案
     * PUT /inference/{reportId}/prevention-plan
     */
    @PutMapping("/{reportId}/prevention-plan")
    public Result<Void> updatePreventionPlan(
            @PathVariable String reportId,
            @Valid @RequestBody PreventionPlanDTO dto) {
        // TODO: 从 SecurityContext 获取当前用户ID，暂时使用默认值
        String authorId = "system";
        inferenceService.updatePreventionPlan(reportId, dto, authorId);
        return Result.success("防治方案修改成功", null);
    }

    /**
     * 获取防治方案详情
     * GET /inference/{reportId}/prevention-plan
     */
    @GetMapping("/{reportId}/prevention-plan")
    public Result<PreventionPlanVO> getPreventionPlan(@PathVariable String reportId) {
        return Result.success(inferenceService.getPreventionPlan(reportId));
    }
}
