package com.agriculture.service.impl;

import com.agriculture.entity.Grid;
import com.agriculture.dao.mapper.GridMapper;
import com.agriculture.service.GridService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 网格区域表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class GridServiceImpl extends ServiceImpl<GridMapper, Grid> implements GridService {

}
