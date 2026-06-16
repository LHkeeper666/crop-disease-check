package com.agriculture.modules.workorder.service.impl;

import com.agriculture.modules.workorder.mapper.WorkOrderHistoryMapper;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.workorder.dto.CallbackDTO;
import com.agriculture.modules.workorder.dto.WorkOrderCreateDTO;
import com.agriculture.modules.workorder.dto.WorkOrderManualCreateDTO;
import com.agriculture.modules.workorder.entity.*;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.workorder.service.WorkOrderService;
import com.agriculture.modules.workorder.vo.CallbackResponseVO;
import com.agriculture.modules.workorder.vo.EmailPreviewVO;
import com.agriculture.modules.workorder.vo.StatusHistoryVO;
import com.agriculture.modules.workorder.vo.WorkOrderDetailVO;
import com.agriculture.modules.workorder.vo.WorkOrderVO;
import com.agriculture.common.websocket.WebSocketService;
import com.agriculture.common.config.LlmProperties;
import com.agriculture.common.service.TemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 工单表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    private BigDecimal extractTopConfidence(List<Map<String, Object>> dets) {
        return dets.stream()
                .map(d -> new BigDecimal(d.getOrDefault("confidence", 0).toString()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    @Resource
    private WorkOrderHistoryMapper workOrderHistoryMapper;

    @Resource
    private com.agriculture.modules.inference.mapper.InferenceMapper inferenceMapper;

    @Resource
    private com.agriculture.modules.user.mapper.SysUserMapper sysUserMapper;

    @Resource
    private WebSocketService webSocketService;

    @Resource
    private TemplateService templateService;

    @Resource
    private LlmProperties llmProperties;

    @Resource
    private RestClient llmRestClient;

    @Override
    public IPage<WorkOrderVO> listWorkOrders(String status, String severity,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              int page, int size) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
               .eq(StringUtils.hasText(severity), WorkOrder::getSeverity, severity)
               .ge(startDate != null, WorkOrder::getCreatedAt, startDate)
               .le(endDate != null, WorkOrder::getCreatedAt, endDate)
               .orderByDesc(WorkOrder::getCreatedAt);

        Page<WorkOrder> pageParam = new Page<>(page, size);
        Page<WorkOrder> result = baseMapper.selectPage(pageParam, wrapper);

        Page<WorkOrderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 带企业隔离的分页查询
     */
    @Override
    public IPage<WorkOrderVO> listWorkOrders(String status, String severity,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              int page, int size, String companyId) {
        return listWorkOrders(status, severity, startDate, endDate, page, size, companyId, null);
    }

    /**
     * 带企业隔离 + 负责人过滤的分页查询
     */
    @Override
    public IPage<WorkOrderVO> listWorkOrders(String status, String severity,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              int page, int size, String companyId, String assignedTo) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(companyId), WorkOrder::getCompanyId, companyId)
               .eq(StringUtils.hasText(assignedTo), WorkOrder::getAssignedTo, assignedTo)
               .eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
               .eq(StringUtils.hasText(severity), WorkOrder::getSeverity, severity)
               .ge(startDate != null, WorkOrder::getCreatedAt, startDate)
               .le(endDate != null, WorkOrder::getCreatedAt, endDate)
               .orderByDesc(WorkOrder::getCreatedAt);

        Page<WorkOrder> pageParam = new Page<>(page, size);
        Page<WorkOrder> result = baseMapper.selectPage(pageParam, wrapper);

        Page<WorkOrderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public WorkOrderDetailVO getWorkOrderDetail(Long id) {
        WorkOrder workOrder = baseMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }

        WorkOrderDetailVO vo = new WorkOrderDetailVO();
        BeanUtils.copyProperties(toVO(workOrder), vo);
        vo.setInferenceId(workOrder.getInferenceId());
        vo.setExpertComment(workOrder.getExpertComment());

        // 填充负责人邮箱
        if (workOrder.getAssignedTo() != null) {
            SysUser assignedUser = sysUserMapper.selectById(workOrder.getAssignedTo());
            if (assignedUser != null) {
                vo.setAssignedToEmail(assignedUser.getEmail());
            }
        }

        // 查询状态历史
        LambdaQueryWrapper<WorkOrderHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(WorkOrderHistory::getWorkorderId, id)
                      .orderByAsc(WorkOrderHistory::getCreatedAt);
        List<WorkOrderHistory> histories = workOrderHistoryMapper.selectList(historyWrapper);
        vo.setStatusHistory(histories.stream().map(h -> {
            StatusHistoryVO svo = new StatusHistoryVO();
            svo.setStatus(h.getStatus());
            svo.setCreatedAt(h.getCreatedAt());
            svo.setOperator(h.getOperatorName());
            return svo;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional
    public Long createWorkOrder(WorkOrderCreateDTO dto, String operatorId, String operatorName, String companyId) {
        // 校验 Inference 存在
        Inference inference = inferenceMapper.selectById(dto.getInferenceId());
        if (inference == null) {
            throw new BusinessException("关联的识别记录不存在");
        }

        // 从 detections JSON 提取信息
        List<Map<String, Object>> dets = parseDetections(inference.getDetections());
        String pestName = extractTopName(dets);
        if (pestName.isEmpty()) pestName = "未知病虫害";
        String pipeline = !dets.isEmpty() ? (String) dets.get(0).get("pipeline") : null;

        // 创建工单
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTitle("【" + dto.getSeverity() + "】" + pestName + " 工单");
        workOrder.setSeverity(dto.getSeverity());
        workOrder.setStatus("PENDING");
        workOrder.setType(pipeline);
        workOrder.setPestName(pestName);
        workOrder.setConfidence(extractTopConfidence(dets));
        workOrder.setInferenceId(dto.getInferenceId());
        workOrder.setAssignedTo(dto.getAssignedTo());
        workOrder.setCreatedBy(operatorId);
        workOrder.setCompanyId(companyId);
        workOrder.setCallbackToken(UUID.randomUUID().toString().replace("-", ""));
        workOrder.setTokenExpireAt(LocalDateTime.now().plusDays(7));
        workOrder.setTokenUsed((byte) 0);
        workOrder.setCreatedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());
        baseMapper.insert(workOrder);

        // 记录初始状态历史
        WorkOrderHistory history = new WorkOrderHistory();
        history.setWorkorderId(workOrder.getId());
        history.setStatus("PENDING");
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setCreatedAt(LocalDateTime.now());
        workOrderHistoryMapper.insert(history);

        // 推送工单创建到 WebSocket
        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("workorderId", workOrder.getId());
            wsData.put("oldStatus", null);
            wsData.put("newStatus", "PENDING");
            wsData.put("operatorName", operatorName);
            wsData.put("type", workOrder.getType());
            wsData.put("severity", workOrder.getSeverity());
            wsData.put("updatedAt", LocalDateTime.now().toString());
            webSocketService.sendWorkorderChange(wsData);
        } catch (Exception e) {
            // 推送失败不影响主流程
        }

        return workOrder.getId();
    }

    @Override
    @Transactional
    public Long createManualWorkOrder(WorkOrderManualCreateDTO dto, String operatorId, String operatorName, String companyId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTitle(dto.getTitle());
        workOrder.setSeverity(dto.getSeverity());
        workOrder.setStatus("PENDING");
        workOrder.setType(dto.getType());
        workOrder.setGridLabel(dto.getGridLabel());
        workOrder.setPestName(dto.getPestName());
        workOrder.setConfidence(dto.getConfidence() != null ? BigDecimal.valueOf(dto.getConfidence()) : null);
        workOrder.setAssignedTo(dto.getAssignedTo());
        workOrder.setCreatedBy(operatorId);
        workOrder.setCompanyId(companyId);
        workOrder.setCallbackToken(UUID.randomUUID().toString().replace("-", ""));
        workOrder.setTokenExpireAt(LocalDateTime.now().plusDays(7));
        workOrder.setTokenUsed((byte) 0);
        workOrder.setCreatedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());
        baseMapper.insert(workOrder);

        // 记录初始状态历史
        WorkOrderHistory history = new WorkOrderHistory();
        history.setWorkorderId(workOrder.getId());
        history.setStatus("PENDING");
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setCreatedAt(LocalDateTime.now());
        workOrderHistoryMapper.insert(history);

        // 推送 WebSocket
        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("workorderId", workOrder.getId());
            wsData.put("oldStatus", null);
            wsData.put("newStatus", "PENDING");
            wsData.put("operatorName", operatorName);
            wsData.put("type", workOrder.getType());
            wsData.put("severity", workOrder.getSeverity());
            wsData.put("updatedAt", LocalDateTime.now().toString());
            webSocketService.sendWorkorderChange(wsData);
        } catch (Exception e) {
            // 推送失败不影响主流程
        }

        return workOrder.getId();
    }

    @Override
    @Transactional
    public CallbackResponseVO handleCallback(CallbackDTO dto) {
        // 查找工单
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrder::getCallbackToken, dto.getToken());
        WorkOrder workOrder = baseMapper.selectOne(wrapper);

        if (workOrder == null) {
            throw new BusinessException(40060, "Token 无效");
        }
        if (workOrder.getTokenExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40061, "Token 已过期");
        }
        if (workOrder.getTokenUsed() == 1) {
            throw new BusinessException(40062, "Token 已使用，该工单已被处理");
        }

        // 根据 action 确定新状态
        String newStatus;
        switch (dto.getAction()) {
            case "CONFIRM":
                newStatus = "DONE";
                break;
            case "IGNORE":
                newStatus = "IGNORED";
                break;
            case "MORE_INFO":
                newStatus = "ESCALATED";
                break;
            default:
                throw new BusinessException("无效的操作类型: " + dto.getAction());
        }

        // 更新工单
        workOrder.setStatus(newStatus);
        workOrder.setExpertComment(dto.getComment());
        workOrder.setTokenUsed((byte) 1);
        workOrder.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(workOrder);

        // 记录状态历史
        WorkOrderHistory history = new WorkOrderHistory();
        history.setWorkorderId(workOrder.getId());
        history.setStatus(newStatus);
        history.setOperatorName("专家回调");
        history.setComment(dto.getComment());
        history.setCreatedAt(LocalDateTime.now());
        workOrderHistoryMapper.insert(history);

        // 推送工单状态变更到 WebSocket
        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("workorderId", workOrder.getId());
            wsData.put("oldStatus", "PENDING");
            wsData.put("newStatus", newStatus);
            wsData.put("operatorName", "专家回调");
            wsData.put("comment", dto.getComment());
            wsData.put("updatedAt", LocalDateTime.now().toString());
            webSocketService.sendWorkorderChange(wsData);
        } catch (Exception e) {
            // 推送失败不影响主流程
        }

        CallbackResponseVO response = new CallbackResponseVO();
        response.setWorkorderId(workOrder.getId());
        response.setNewStatus(newStatus);
        return response;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, String comment, String operatorId, String operatorName) {
        WorkOrder workOrder = baseMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }

        // 校验状态流转合法性
        String oldStatus = workOrder.getStatus();
        if (!isValidTransition(oldStatus, status)) {
            throw new BusinessException("非法的状态变更: " + oldStatus + " -> " + status);
        }

        workOrder.setStatus(status);
        workOrder.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(workOrder);

        // 记录状态历史
        WorkOrderHistory history = new WorkOrderHistory();
        history.setWorkorderId(id);
        history.setStatus(status);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setComment(comment);
        history.setCreatedAt(LocalDateTime.now());
        workOrderHistoryMapper.insert(history);

        // 推送 WebSocket
        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("workorderId", id);
            wsData.put("oldStatus", oldStatus);
            wsData.put("newStatus", status);
            wsData.put("operatorName", operatorName);
            wsData.put("updatedAt", LocalDateTime.now().toString());
            webSocketService.sendWorkorderChange(wsData);
        } catch (Exception e) {
            // 推送失败不影响主流程
        }
    }

    @Override
    public void updateSeverity(Long id, String severity) {
        WorkOrder workOrder = baseMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }
        // 仅允许在非终态时修改严重程度
        if ("DONE".equals(workOrder.getStatus()) || "IGNORED".equals(workOrder.getStatus())) {
            throw new BusinessException("已完成或已忽略的工单不能修改严重程度");
        }
        workOrder.setSeverity(severity);
        workOrder.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(workOrder);
    }

    @Override
    @Transactional
    public void updateAssignee(Long id, String assignedTo) {
        WorkOrder workOrder = baseMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }
        // 仅允许在非终态时修改指派专家
        if ("DONE".equals(workOrder.getStatus()) || "IGNORED".equals(workOrder.getStatus())) {
            throw new BusinessException("已完成或已忽略的工单不能修改指派专家");
        }
        // 校验专家用户是否存在
        SysUser expert = sysUserMapper.selectById(assignedTo);
        if (expert == null) {
            throw new BusinessException("指派的专家用户不存在");
        }
        workOrder.setAssignedTo(assignedTo);
        workOrder.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(workOrder);
    }

    @Override
    @Transactional
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = baseMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }
        // 删除关联的历史记录
        LambdaQueryWrapper<WorkOrderHistory> hw = new LambdaQueryWrapper<>();
        hw.eq(WorkOrderHistory::getWorkorderId, id);
        workOrderHistoryMapper.delete(hw);
        // 删除工单
        baseMapper.deleteById(id);
    }

    @Override
    public EmailPreviewVO previewEmail(Long workOrderId) {
        WorkOrder workOrder = baseMapper.selectById(workOrderId);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }

        // 查找收件人（工单负责人）
        if (workOrder.getAssignedTo() == null) {
            throw new BusinessException("该工单未指定负责人，请先指定负责人后再发送邮件");
        }
        SysUser expert = sysUserMapper.selectById(workOrder.getAssignedTo());
        if (expert == null || expert.getEmail() == null || expert.getEmail().isEmpty()) {
            throw new BusinessException("该专家未配置邮箱地址");
        }

        // 使用 AI 生成邮件内容
        String content = generateEmailContent(workOrder);

        EmailPreviewVO preview = new EmailPreviewVO();
        preview.setToUserId(expert.getId());
        preview.setToName(expert.getName());
        preview.setToEmail(expert.getEmail());
        preview.setSubject("【农作物疾病检测系统】工单通知 - " + workOrder.getTitle());
        preview.setContent(content);
        return preview;
    }

    private String generateEmailContent(WorkOrder workOrder) {
        try {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("title", workOrder.getTitle());
            attrs.put("severity", workOrder.getSeverity());
            attrs.put("gridLabel", workOrder.getGridLabel() != null ? workOrder.getGridLabel() : "无");
            attrs.put("pestName", workOrder.getPestName() != null ? workOrder.getPestName() : "无");
            attrs.put("confidence", workOrder.getConfidence() != null ? workOrder.getConfidence().multiply(new BigDecimal(100)).stripTrailingZeros().toPlainString() + "%" : "无");
            attrs.put("status", workOrder.getStatus());
            attrs.put("createdAt", workOrder.getCreatedAt() != null ? workOrder.getCreatedAt().toString() : "未知");

            String prompt = templateService.render("email_prompt", attrs);

            // 调用 LLM 生成邮件内容
            Map<String, Object> requestBody = Map.of(
                    "model", llmProperties.getModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", false
            );

            String responseJson = llmRestClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // 解析响应
            JsonNode root = JSON.readTree(responseJson);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.size() > 0) {
                return choices.get(0).get("message").get("content").asText();
            }
        } catch (Exception e) {
            // AI 生成失败时回退到结构化内容
        }

        // 回退：生成结构化邮件内容
        return "尊敬的专家：\n\n"
                + "您有一条新的工单通知，请及时处理。\n\n"
                + "━━━━━━━━━━━━━━━━━━━━\n"
                + "工单标题：" + workOrder.getTitle() + "\n"
                + "严重程度：" + workOrder.getSeverity() + "\n"
                + "网格区域：" + (workOrder.getGridLabel() != null ? workOrder.getGridLabel() : "无") + "\n"
                + "病虫害：" + (workOrder.getPestName() != null ? workOrder.getPestName() : "无") + "\n"
                + "置信度：" + (workOrder.getConfidence() != null ? workOrder.getConfidence().multiply(new BigDecimal(100)).stripTrailingZeros().toPlainString() + "%" : "无") + "\n"
                + "创建时间：" + workOrder.getCreatedAt() + "\n\n"
                + "请登录系统查看详情并处理。\n\n"
                + "—— 农作物疾病检测系统";
    }

    /**
     * 校验状态流转是否合法
     */
    private boolean isValidTransition(String from, String to) {
        switch (from) {
            case "PENDING":
                return "PROCESSING".equals(to) || "IGNORED".equals(to) || "ESCALATED".equals(to);
            case "PROCESSING":
                return "DONE".equals(to) || "ESCALATED".equals(to);
            case "IGNORED":
                return "PENDING".equals(to); // 恢复待处理
            default:
                return false;
        }
    }

    private WorkOrderVO toVO(WorkOrder workOrder) {
        WorkOrderVO vo = new WorkOrderVO();
        BeanUtils.copyProperties(workOrder, vo);

        // 关联查询指派人姓名
        if (workOrder.getAssignedTo() != null) {
            SysUser user = sysUserMapper.selectById(workOrder.getAssignedTo());
            if (user != null) {
                vo.setAssignedToName(user.getName());
            }
        }

        return vo;
    }
}
