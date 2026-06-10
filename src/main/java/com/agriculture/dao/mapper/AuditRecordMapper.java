package com.agriculture.dao.mapper;

import com.agriculture.entity.AuditRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 审核记录表 Mapper 接口
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Mapper
public interface AuditRecordMapper extends BaseMapper<AuditRecord> {

}
