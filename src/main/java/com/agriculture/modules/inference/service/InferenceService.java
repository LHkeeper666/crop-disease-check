package com.agriculture.modules.inference.service;

import com.agriculture.modules.pestDiseaseInfo.dto.AuditDTO;
import com.agriculture.modules.pestDiseaseInfo.dto.PreventionPlanDTO;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.pestDiseaseInfo.vo.PendingAuditVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PendingReviewVO;
import com.agriculture.modules.pestDiseaseInfo.vo.PreventionPlanVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 识别结果表 服务类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
public interface InferenceService extends IService<Inference> {

    /**
     * 待复核列表（低置信度识别结果）
     */
    IPage<PendingReviewVO> listPendingReview(String sortByConfidence, int page, int size);

    /**
     * 待审核列表（已识别、待专家审核的上报）
     */
    IPage<PendingAuditVO> listPendingAudit(int page, int size);

    /**
     * 审核上报
     */
    void auditReport(String reportId, AuditDTO dto, String auditorId);

    /**
     * 制定防治方案
     */
    void createPreventionPlan(String reportId, PreventionPlanDTO dto, String authorId);

    /**
     * 修改防治方案（保留历史版本）
     */
    void updatePreventionPlan(String reportId, PreventionPlanDTO dto, String authorId);

    /**
     * 获取防治方案详情
     */
    PreventionPlanVO getPreventionPlan(String reportId);

    /**
     * 查询检测记录（企业隔离）
     */
    List<Map<String, Object>> listDetections(String companyId, String type,
                                              String startDate, String endDate,
                                              int limit);

    /**
     * 获取检测趋势统计（企业隔离）
     */
    Map<String, Object> getDetectionTrend(String companyId, int days);
}
