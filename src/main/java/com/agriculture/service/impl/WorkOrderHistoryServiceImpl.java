package com.agriculture.service.impl;

import com.agriculture.entity.WorkOrderHistory;
import com.agriculture.dao.mapper.WorkOrderHistoryMapper;
import com.agriculture.service.WorkOrderHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 工单状态历史 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class WorkOrderHistoryServiceImpl extends ServiceImpl<WorkOrderHistoryMapper, WorkOrderHistory> implements WorkOrderHistoryService {

}
