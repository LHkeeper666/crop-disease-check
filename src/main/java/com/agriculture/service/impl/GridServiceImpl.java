package com.agriculture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.agriculture.dao.mapper.GridMapper;
import com.agriculture.dto.GridCreateDTO;
import com.agriculture.dto.GridUpdateDTO;
import com.agriculture.entity.Grid;
import com.agriculture.exception.BusinessException;
import com.agriculture.service.GridService;
import com.agriculture.vo.GridVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 网格区域服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GridServiceImpl extends ServiceImpl<GridMapper, Grid> implements GridService {

    private final GridMapper gridMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<GridVO> listGrids(String greenhouseId) {
        LambdaQueryWrapper<Grid> wrapper = new LambdaQueryWrapper<>();
        if (greenhouseId != null && !greenhouseId.isEmpty()) {
            wrapper.eq(Grid::getGreenhouseId, greenhouseId);
        }
        wrapper.orderByDesc(Grid::getCreatedAt);
        List<Grid> grids = gridMapper.selectList(wrapper);

        return grids.stream().map(grid -> {
            GridVO vo = new GridVO();
            BeanUtil.copyProperties(grid, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String createGrid(GridCreateDTO dto) {
        // 检查编号是否重复
        LambdaQueryWrapper<Grid> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Grid::getLabel, dto.getLabel());
        if (gridMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("网格编号已存在");
        }

        Grid grid = new Grid();
        grid.setId(IdUtil.fastSimpleUUID());
        grid.setLabel(dto.getLabel());
        grid.setGreenhouseId(dto.getGreenhouseId());
        grid.setCropType(dto.getCropType());
        grid.setCreatedAt(LocalDateTime.now());
        grid.setUpdatedAt(LocalDateTime.now());
        grid.setDeleted((byte) 0);

        // 处理多边形坐标
        if (dto.getPolygonCoords() != null) {
            try {
                grid.setPolygonCoords(objectMapper.writeValueAsString(dto.getPolygonCoords()));
            } catch (JsonProcessingException e) {
                throw new BusinessException("坐标格式错误");
            }
        }

        gridMapper.insert(grid);
        log.info("网格创建成功: {}, 编号: {}", grid.getId(), grid.getLabel());
        return grid.getId();
    }

    @Override
    @Transactional
    public void updateGrid(String id, GridUpdateDTO dto) {
        Grid grid = gridMapper.selectById(id);
        if (grid == null) {
            throw new BusinessException("网格不存在");
        }

        // 检查编号是否重复（排除自身）
        if (dto.getLabel() != null && !dto.getLabel().equals(grid.getLabel())) {
            LambdaQueryWrapper<Grid> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Grid::getLabel, dto.getLabel());
            wrapper.ne(Grid::getId, id);
            if (gridMapper.selectCount(wrapper) > 0) {
                throw new BusinessException("网格编号已存在");
            }
            grid.setLabel(dto.getLabel());
        }

        if (dto.getGreenhouseId() != null) {
            grid.setGreenhouseId(dto.getGreenhouseId());
        }
        if (dto.getCropType() != null) {
            grid.setCropType(dto.getCropType());
        }
        if (dto.getPolygonCoords() != null) {
            try {
                grid.setPolygonCoords(objectMapper.writeValueAsString(dto.getPolygonCoords()));
            } catch (JsonProcessingException e) {
                throw new BusinessException("坐标格式错误");
            }
        }

        grid.setUpdatedAt(LocalDateTime.now());
        gridMapper.updateById(grid);
        log.info("网格更新成功: {}", id);
    }

    @Override
    @Transactional
    public void deleteGrid(String id) {
        Grid grid = gridMapper.selectById(id);
        if (grid == null) {
            throw new BusinessException("网格不存在");
        }

        // 逻辑删除
        grid.setDeleted((byte) 1);
        grid.setUpdatedAt(LocalDateTime.now());
        gridMapper.updateById(grid);
        log.info("网格删除成功: {}", id);
    }
}
