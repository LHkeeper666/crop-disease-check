package com.agriculture.modules.inference.mapper;

import com.agriculture.modules.inference.entity.Inference;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 识别结果表 Mapper 接口
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Mapper
public interface InferenceMapper extends BaseMapper<Inference> {

}
