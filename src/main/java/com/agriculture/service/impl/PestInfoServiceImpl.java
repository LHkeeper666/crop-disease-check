package com.agriculture.service.impl;

import com.agriculture.entity.PestInfo;
import com.agriculture.dao.mapper.PestInfoMapper;
import com.agriculture.service.PestInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 病虫害知识库 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class PestInfoServiceImpl extends ServiceImpl<PestInfoMapper, PestInfo> implements PestInfoService {

}
