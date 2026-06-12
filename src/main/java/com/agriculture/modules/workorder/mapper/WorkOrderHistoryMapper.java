package com.agriculture.modules.workorder.mapper;

import com.agriculture.modules.workorder.entity.WorkOrderHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 工单状态历史 Mapper 接口
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Mapper
public interface WorkOrderHistoryMapper extends BaseMapper<WorkOrderHistory> {

}
