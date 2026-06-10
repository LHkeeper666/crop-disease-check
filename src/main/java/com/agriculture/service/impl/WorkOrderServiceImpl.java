package com.agriculture.service.impl;

import com.agriculture.dao.mapper.WorkOrderHistoryMapper;
import com.agriculture.dao.mapper.WorkOrderMapper;
import com.agriculture.dto.CallbackDTO;
import com.agriculture.dto.WorkOrderCreateDTO;
import com.agriculture.entity.*;
import com.agriculture.exception.BusinessException;
import com.agriculture.service.WorkOrderService;
import com.agriculture.vo.CallbackResponseVO;
import com.agriculture.vo.StatusHistoryVO;
import com.agriculture.vo.WorkOrderDetailVO;
import com.agriculture.vo.WorkOrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService {

    @Resource
    private WorkOrderHistoryMapper workOrderHistoryMapper;

    @Resource
    private com.agriculture.dao.mapper.InferenceMapper inferenceMapper;

    @Resource
    private com.agriculture.dao.mapper.SysUserMapper sysUserMapper;

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

    @Override
    public WorkOrderDetailVO getWorkOrderDetail(String id) {
        WorkOrder workOrder = baseMapper.selectById(id);
        if (workOrder == null) {
            throw new BusinessException(404, "工单不存在");
        }

        WorkOrderDetailVO vo = new WorkOrderDetailVO();
        BeanUtils.copyProperties(toVO(workOrder), vo);
        vo.setInferenceId(workOrder.getInferenceId());
        vo.setExpertComment(workOrder.getExpertComment());

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
    public String createWorkOrder(WorkOrderCreateDTO dto, String operatorId, String operatorName) {
        // 校验 Inference 存在
        Inference inference = inferenceMapper.selectById(dto.getInferenceId());
        if (inference == null) {
            throw new BusinessException("关联的识别记录不存在");
        }

        // 从 Inference 获取反冗余字段
        String pestName = inference.getPestName() != null ? inference.getPestName() : "未知病虫害";
        String pipeline = inference.getPipeline();

        // 创建工单
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTitle("【" + dto.getSeverity() + "】" + pestName + " 工单");
        workOrder.setSeverity(dto.getSeverity());
        workOrder.setStatus("PENDING");
        workOrder.setType(pipeline);
        workOrder.setPestName(pestName);
        workOrder.setConfidence(inference.getConfidence());
        workOrder.setInferenceId(dto.getInferenceId());
        workOrder.setAssignedTo(dto.getAssignedTo());
        workOrder.setCreatedBy(operatorId);
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

        CallbackResponseVO response = new CallbackResponseVO();
        response.setWorkorderId(workOrder.getId());
        response.setNewStatus(newStatus);
        return response;
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
