package com.agriculture.modules.workorder.mapper;

import com.agriculture.modules.workorder.entity.WorkOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 工单表 Mapper 接口
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

}
