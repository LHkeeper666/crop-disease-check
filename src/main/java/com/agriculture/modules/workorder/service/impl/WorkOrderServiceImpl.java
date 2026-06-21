package com.agriculture.modules.workorder.service.impl;

import com.agriculture.common.mq.event.DetectionEvent;
import com.agriculture.modules.workorder.mapper.WorkOrderHistoryMapper;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.workorder.dto.CallbackDTO;
import com.agriculture.modules.workorder.dto.WorkOrderCreateDTO;
import com.agriculture.modules.workorder.dto.WorkOrderManualCreateDTO;
import com.agriculture.modules.workorder.entity.*;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.grid.mapper.GridMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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

    private static final Logger log = LoggerFactory.getLogger(WorkOrderServiceImpl.class);
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

    @Resource
    private GridMapper gridMapper;

    @Value("${camera.detect.auto-workorder-confidence:0.5}")
    private float autoWorkOrderConfidence;

    @Value("${workorder.ai-assign-threshold:0.6}")
    private float aiAssignThreshold;

    @Override
    public IPage<WorkOrderVO> listWorkOrders(String status, String severity,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              int page, int size) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
               .ne(!StringUtils.hasText(status), WorkOrder::getStatus, WorkOrder.STATUS_AI_REVIEW)
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
               .eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
               .ne(!StringUtils.hasText(status), WorkOrder::getStatus, WorkOrder.STATUS_AI_REVIEW)
               .eq(StringUtils.hasText(severity), WorkOrder::getSeverity, severity)
               .ge(startDate != null, WorkOrder::getCreatedAt, startDate)
               .le(endDate != null, WorkOrder::getCreatedAt, endDate)
               .orderByDesc(WorkOrder::getCreatedAt);

        // 专家/基层员工：显示当前指派给自己 + 自己曾操作过的工单
        if (StringUtils.hasText(assignedTo)) {
            String historySubQuery = "SELECT workorder_id FROM work_order_history WHERE operator_id = '" + assignedTo + "'";
            wrapper.and(w -> w.eq(WorkOrder::getAssignedTo, assignedTo)
                    .or().inSql(WorkOrder::getId, historySubQuery));
        }

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
        workOrder.setImageUrl(dto.getImageUrl());

        // 如果有原始图URL，创建 inference 记录存储两张图片，供前端按角色展示
        if (dto.getOriginalImageUrl() != null && dto.getImageUrl() != null) {
            Inference imgInference = new Inference();
            imgInference.setId(UUID.randomUUID().toString());
            imgInference.setAnnotatedImageUrl(dto.getImageUrl());
            imgInference.setOriginalImageUrl(dto.getOriginalImageUrl());
            imgInference.setCompanyId(companyId);
            imgInference.setSourceType("REPORT");
            imgInference.setCreatedAt(LocalDateTime.now());
            inferenceMapper.insert(imgInference);
            workOrder.setInferenceId(imgInference.getId());
        }
        // 根据指派角色设置 expert_comment
        if (dto.getAssignedTo() != null) {
            SysUser assignee = sysUserMapper.selectById(dto.getAssignedTo());
            if (assignee != null && "EXPERT".equals(assignee.getRole())) {
                workOrder.setExpertComment("请您复查");
            } else if (assignee != null && "STAFF".equals(assignee.getRole())) {
                workOrder.setExpertComment(generateMeasureComment(workOrder));
            }
        }
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
    public void updateStatus(Long id, String status, String comment, String expertComment, String operatorId, String operatorName) {
        WorkOrder workOrder = baseMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }

        // 校验状态流转合法性
        String oldStatus = workOrder.getStatus();
        if (!isValidTransition(oldStatus, status)) {
            throw new BusinessException("非法的状态变更: " + oldStatus + " -> " + status);
        }

        // 专家确认处理时：保存专家评语并自动指派给基层员工
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator != null && "EXPERT".equals(operator.getRole()) && "PROCESSING".equals(status)) {
            if (expertComment != null && !expertComment.trim().isEmpty()) {
                workOrder.setExpertComment(expertComment.trim());
            }
            reassignToRandomStaff(workOrder);
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
                    .uri("/v1/chat/completions")
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

    // ==================== AI 审核逻辑 ====================

    /**
     * 审核候选工单列表，过滤不合理的检测结果。
     * 流程：确定性前缀过滤 → LLM 审核 → 返回通过审核的候选列表。
     * 不操作数据库，仅做纯过滤。LLM 异常时默认全部通过。
     */
    private List<WorkOrder> reviewWorkOrders(List<WorkOrder> candidates) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();

        try {
            // 1. 获取每个候选对应网格的作物类型
            Map<Long, String> cropTypeMap = new HashMap<>();
            for (WorkOrder wo : candidates) {
                String cropType = "未知";
                if (wo.getGridLabel() != null) {
                    LambdaQueryWrapper<Grid> gw = new LambdaQueryWrapper<>();
                    gw.eq(Grid::getLabel, wo.getGridLabel());
                    Grid grid = gridMapper.selectOne(gw);
                    if (grid != null && grid.getCropType() != null) {
                        cropType = grid.getCropType();
                    }
                }
                cropTypeMap.put(wo.getId(), cropType);
            }

            // 2. 确定性过滤：用 Grid 作物类型匹配病虫害名称前缀
            Set<String> allCropTypes = new HashSet<>();
            LambdaQueryWrapper<Grid> cropQuery = new LambdaQueryWrapper<>();
            cropQuery.select(Grid::getCropType).isNotNull(Grid::getCropType);
            for (Grid g : gridMapper.selectList(cropQuery)) {
                allCropTypes.add(g.getCropType());
            }
            Iterator<WorkOrder> it = candidates.iterator();
            while (it.hasNext()) {
                WorkOrder wo = it.next();
                String pestName = wo.getPestName();
                String gridCrop = cropTypeMap.get(wo.getId());
                if (pestName == null || gridCrop == null || "未知".equals(gridCrop)) continue;

                String matchedCrop = null;
                for (String crop : allCropTypes) {
                    if (pestName.startsWith(crop)) {
                        matchedCrop = crop;
                        break;
                    }
                }

                if (matchedCrop != null && !matchedCrop.equals(gridCrop)) {
                    log.info("前缀匹配过滤: pestName={}, gridCrop={}, 匹配到作物前缀={}, 过滤跨物种候选",
                            pestName, gridCrop, matchedCrop);
                    it.remove();
                    cropTypeMap.remove(wo.getId());
                }
            }
            if (candidates.isEmpty()) return Collections.emptyList();

            // 3. 确定当前季节
            int month = LocalDateTime.now().getMonthValue();
            String season = getSeason(month);

            // 4. 构建候选列表字符串
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++) {
                WorkOrder wo = candidates.get(i);
                String cropType = cropTypeMap.get(wo.getId());
                int pct = wo.getConfidence() != null
                        ? wo.getConfidence().multiply(new BigDecimal(100)).intValue() : 0;
                sb.append(String.format("[%d] 网格%s / 作物：%s / 病虫害：%s / 置信度：%d%%",
                        i + 1, wo.getGridLabel(), cropType, wo.getPestName(), pct));
                if (i < candidates.size() - 1) sb.append("\n");
            }

            // 5. 渲染 prompt + 调用 LLM
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("month", month);
            attrs.put("season", season);
            attrs.put("candidates", sb.toString());
            String prompt = templateService.render("ai_review_prompt", attrs);

            Map<String, Object> requestBody = Map.of(
                    "model", llmProperties.getModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", false
            );

            String responseJson = llmRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // 6. 解析响应
            JsonNode root = JSON.readTree(responseJson);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return candidates;
            }

            String content = choices.get(0).get("message").get("content").asText();
            content = extractJsonArray(content);

            JsonNode results = JSON.readTree(content);
            if (!results.isArray() || results.size() != candidates.size()) {
                return candidates;
            }

            // 7. 按审核结果过滤
            List<WorkOrder> approved = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                JsonNode result = results.get(i);
                boolean reasonable = result.has("reasonable") && result.get("reasonable").asBoolean(true);
                if (reasonable) {
                    approved.add(candidates.get(i));
                } else {
                    log.info("LLM审核不通过: pestName={}, reason={}",
                            candidates.get(i).getPestName(),
                            result.has("reason") ? result.get("reason").asText() : "无");
                }
            }
            return approved;

        } catch (Exception e) {
            log.error("AI 审核异常，已通过前缀过滤的候选保留，其余丢弃", e);
            return Collections.emptyList();
        }
    }

    private String getSeason(int month) {
        if (month >= 3 && month <= 5) return "春季";
        if (month >= 6 && month <= 8) return "夏季";
        if (month >= 9 && month <= 11) return "秋季";
        return "冬季";
    }

    private String extractJsonArray(String text) {
        text = text.trim();
        // 处理 markdown 代码块包裹的情况
        if (text.contains("```")) {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1);
            }
        }
        // 直接找 JSON 数组
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 根据置信度自动指派工单负责人：置信度 >= 阈值 → STAFF，置信度 < 阈值 → EXPERT。
     * 在同企业内随机选取活跃用户，返回被指派者姓名，未找到合适人选时返回 null。
     */
    private String assignByConfidence(WorkOrder wo) {
        String targetRole = wo.getConfidence() != null
                && wo.getConfidence().compareTo(BigDecimal.valueOf(aiAssignThreshold)) >= 0
                ? "STAFF" : "EXPERT";

        // 根据指派角色设置 expert_comment
        if ("STAFF".equals(targetRole)) {
            wo.setExpertComment(generateMeasureComment(wo));
        } else {
            wo.setExpertComment("请您复查");
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0)
               .eq(SysUser::getRole, targetRole)
               .eq(SysUser::getStatus, "ACTIVE")
               .eq(StringUtils.hasText(wo.getCompanyId()), SysUser::getCompanyId, wo.getCompanyId());

        List<SysUser> candidates = sysUserMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            log.warn("AI审核指派失败: 企业 {} 内无可用 {}, workOrderId={}", wo.getCompanyId(), targetRole, wo.getId());
            return null;
        }

        SysUser assignee = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        wo.setAssignedTo(assignee.getId());
        log.info("AI审核指派: workOrderId={}, role={}, assignee={}({}), confidence={}",
                wo.getId(), targetRole, assignee.getName(), assignee.getId(), wo.getConfidence());
        return assignee.getName();
    }

    /**
     * 专家确认处理后，自动将工单指派给企业内随机一名基层员工
     */
    private void reassignToRandomStaff(WorkOrder wo) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0)
               .eq(SysUser::getRole, "STAFF")
               .eq(SysUser::getStatus, "ACTIVE")
               .eq(StringUtils.hasText(wo.getCompanyId()), SysUser::getCompanyId, wo.getCompanyId());
        List<SysUser> candidates = sysUserMapper.selectList(wrapper);
        if (!candidates.isEmpty()) {
            SysUser staff = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            wo.setAssignedTo(staff.getId());
            log.info("专家确认后自动指派基层员工: workOrderId={}, newAssignee={}({})", wo.getId(), staff.getName(), staff.getId());
        } else {
            log.warn("专家确认后指派失败: 企业 {} 内无可用基层员工, workOrderId={}", wo.getCompanyId(), wo.getId());
        }
    }

    /**
     * 为指派给基层员工的工单生成 AI 防治建议（expert_comment）
     */
    private String generateMeasureComment(WorkOrder wo) {
        try {
            String pestName = wo.getPestName() != null ? wo.getPestName() : "未知病虫害";
            String gridLabel = wo.getGridLabel() != null ? wo.getGridLabel() : "未知地块";
            String severity = wo.getSeverity() != null ? wo.getSeverity() : "未知";
            String confidence = wo.getConfidence() != null
                    ? wo.getConfidence().multiply(new BigDecimal(100)).stripTrailingZeros().toPlainString() + "%" : "未知";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("pestName", pestName);
            attrs.put("gridLabel", gridLabel);
            attrs.put("severity", severity);
            attrs.put("confidence", confidence);
            String prompt = templateService.render("measure_comment", attrs);

            Map<String, Object> requestBody = Map.of(
                    "model", llmProperties.getModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", false
            );

            String responseJson = llmRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = JSON.readTree(responseJson);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.size() > 0) {
                String content = choices.get(0).get("message").get("content").asText();
                return content.length() > 500 ? content.substring(0, 497) + "..." : content;
            }
        } catch (Exception e) {
            log.warn("AI生成防治建议失败，使用默认内容", e);
        }
        String pest = wo.getPestName() != null ? wo.getPestName() : "病虫害";
        return "现场确认" + pest + "症状，及时采取物理或化学防治措施，并加强田间巡查预防复发。";
    }

    // ==================== MQ 驱动的智能工单生成 ====================

    private enum SeverityLevel {
        LOW, MEDIUM, HIGH, CRITICAL;

        static SeverityLevel fromConfidence(double confidence) {
            if (confidence >= 0.8) return CRITICAL;
            if (confidence >= 0.6) return HIGH;
            if (confidence >= 0.4) return MEDIUM;
            return LOW;
        }

        String toWorkOrderSeverity() {
            switch (this) {
                case CRITICAL: return "CRITICAL";
                case HIGH:     return "HIGH";
                case MEDIUM:   return "MEDIUM";
                default:       return "LOW";
            }
        }
    }

    @Override
    public void createFromDetectionEvent(DetectionEvent event) {
        // 1. 收集候选对象（不入库），同时处理去重
        List<WorkOrder> candidates = new ArrayList<>();

        for (String gridLabel : event.getGridLabels()) {
            for (DetectionEvent.PestDetection det : event.getDetections()) {
                if (det.getConfidence() < autoWorkOrderConfidence) continue;

                try {
                    // 去重：同企业 + 同网格 + 同病虫害 + 活跃工单
                    LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(WorkOrder::getCompanyId, event.getCompanyId())
                           .eq(WorkOrder::getGridLabel, gridLabel)
                           .eq(WorkOrder::getPestName, det.getNameCn())
                           .in(WorkOrder::getStatus, "PENDING", "PROCESSING");
                    WorkOrder existing = baseMapper.selectOne(wrapper);

                    if (existing != null) {
                        // 已有活跃工单，仅更新置信度
                        if (BigDecimal.valueOf(det.getConfidence()).compareTo(existing.getConfidence()) > 0) {
                            existing.setConfidence(BigDecimal.valueOf(det.getConfidence()));
                            existing.setUpdatedAt(LocalDateTime.now());
                            baseMapper.updateById(existing);
                        }
                    } else {
                        // 构建候选对象（暂不入库）
                        SeverityLevel level = SeverityLevel.fromConfidence(det.getConfidence());
                        WorkOrder wo = new WorkOrder();
                        wo.setTitle("【" + level.toWorkOrderSeverity() + "】Grid-" + gridLabel
                                + " " + det.getNameCn() + " 自动检测");
                        wo.setSeverity(level.toWorkOrderSeverity());
                        wo.setStatus("PENDING");
                        wo.setType(det.getType());
                        wo.setInferenceId(event.getInferenceId());
                        wo.setGridLabel(gridLabel);
                        wo.setPestName(det.getNameCn());
                        wo.setConfidence(BigDecimal.valueOf(det.getConfidence()));
                        wo.setCompanyId(event.getCompanyId());
                        wo.setCallbackToken(UUID.randomUUID().toString().replace("-", ""));
                        wo.setTokenExpireAt(LocalDateTime.now().plusDays(7));
                        wo.setTokenUsed((byte) 0);
                        wo.setCreatedAt(LocalDateTime.now());
                        wo.setUpdatedAt(LocalDateTime.now());
                        candidates.add(wo);
                    }
                } catch (Exception e) {
                    log.warn("收集检测候选失败: gridLabel={}, det={}", gridLabel, det.getNameCn(), e);
                }
            }
        }

        if (candidates.isEmpty()) return;

        // 2. AI 审核：前缀过滤 + LLM 审核，只返回通过的候选
        List<WorkOrder> approved = reviewWorkOrders(candidates);

        // 3. 只为通过审核的候选入库
        for (WorkOrder wo : approved) {
            try {
                // 指派负责人
                String assigneeName = assignByConfidence(wo);

                baseMapper.insert(wo);

                WorkOrderHistory history = new WorkOrderHistory();
                history.setWorkorderId(wo.getId());
                history.setStatus("PENDING");
                history.setOperatorName("AI审核通过");
                history.setCreatedAt(LocalDateTime.now());
                workOrderHistoryMapper.insert(history);

                try {
                    Map<String, Object> wsData = new HashMap<>();
                    wsData.put("workorderId", wo.getId());
                    wsData.put("oldStatus", null);
                    wsData.put("newStatus", "PENDING");
                    wsData.put("operatorName", "AI审核通过");
                    wsData.put("type", wo.getType());
                    wsData.put("severity", wo.getSeverity());
                    wsData.put("assignedTo", wo.getAssignedTo());
                    wsData.put("assignedToName", assigneeName);
                    wsData.put("updatedAt", LocalDateTime.now().toString());
                    webSocketService.sendWorkorderChange(wsData);
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                log.error("审核通过工单入库失败: pestName={}", wo.getPestName(), e);
            }
        }

        log.info("智能工单生成完成: 候选{}条, 通过{}条, 过滤{}条",
                candidates.size(), approved.size(), candidates.size() - approved.size());
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

        // 关联查询推理记录的图片
        // image_url 优先使用工单自身的值，original_image_url 始终从 inference 表获取
        if (workOrder.getImageUrl() != null) {
            vo.setImageUrl(workOrder.getImageUrl());
        }
        if (workOrder.getInferenceId() != null) {
            Inference inference = inferenceMapper.selectById(workOrder.getInferenceId());
            if (inference != null) {
                if (vo.getImageUrl() == null) {
                    vo.setImageUrl(inference.getAnnotatedImageUrl());
                }
                vo.setOriginalImageUrl(inference.getOriginalImageUrl());
            }
        }

        return vo;
    }
}
