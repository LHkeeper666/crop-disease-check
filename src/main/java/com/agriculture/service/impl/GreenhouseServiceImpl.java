package com.agriculture.service.impl;

import com.agriculture.dao.mapper.CameraGridMapper;
import com.agriculture.dao.mapper.GreenhouseMapper;
import com.agriculture.dao.mapper.GridMapper;
import com.agriculture.dto.GreenhouseDTO;
import com.agriculture.entity.CameraGrid;
import com.agriculture.entity.Greenhouse;
import com.agriculture.entity.Grid;
import com.agriculture.exception.BusinessException;
import com.agriculture.service.GreenhouseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 温室/大棚表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-10
 */
@Service
public class GreenhouseServiceImpl extends ServiceImpl<GreenhouseMapper, Greenhouse> implements GreenhouseService {

    @Resource
    private GridMapper gridMapper;

    @Resource
    private CameraGridMapper cameraGridMapper;

    @Override
    public IPage<Greenhouse> listGreenhouses(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Greenhouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), Greenhouse::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Greenhouse::getSectorId, keyword)
                    .or()
                    .like(Greenhouse::getCropSpecies, keyword));
        }
        wrapper.orderByDesc(Greenhouse::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Greenhouse getGreenhouseDetail(String id) {
        Greenhouse greenhouse = getById(id);
        if (greenhouse == null) {
            throw new BusinessException(40112, "温室不存在");
        }
        // 统计关联网格数
        Long gridCount = gridMapper.selectCount(
                new QueryWrapper<Grid>().eq("greenhouse_id", id));
        greenhouse.setGridCount(gridCount.intValue());

        // 统计关联摄像头数（通过 grid -> camera_grid 关联）
        List<Grid> grids = gridMapper.selectList(
                new QueryWrapper<Grid>()
                        .select("id")
                        .eq("greenhouse_id", id));
        if (grids.isEmpty()) {
            greenhouse.setCameraCount(0);
        } else {
            List<String> gridIds = grids.stream().map(Grid::getId).collect(Collectors.toList());
            Long cameraCount = cameraGridMapper.selectCount(
                    new QueryWrapper<CameraGrid>()
                            .in("grid_id", gridIds));
            greenhouse.setCameraCount(cameraCount.intValue());
        }
        return greenhouse;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createGreenhouse(GreenhouseDTO dto, String companyId) {
        // sectorId 格式校验
        if (!StringUtils.hasText(dto.getSectorId())) {
            throw new BusinessException(40111, "区域编号格式不正确");
        }
        // sectorId 唯一校验
        Long exists = count(new LambdaQueryWrapper<Greenhouse>()
                .eq(Greenhouse::getSectorId, dto.getSectorId()));
        if (exists > 0) {
            throw new BusinessException(40110, "区域编号已存在");
        }

        Greenhouse entity = new Greenhouse();
        entity.setSectorId(dto.getSectorId());
        entity.setCropSpecies(dto.getCropSpecies());
        entity.setPlantingDate(dto.getPlantingDate());
        entity.setLocation(dto.getLocation());
        entity.setArea(dto.getArea());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        entity.setCompanyId(companyId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGreenhouse(String id, GreenhouseDTO dto) {
        Greenhouse existing = getById(id);
        if (existing == null) {
            throw new BusinessException(40112, "温室不存在");
        }
        // sectorId 唯一校验（如果修改了 sectorId）
        if (StringUtils.hasText(dto.getSectorId()) && !dto.getSectorId().equals(existing.getSectorId())) {
            Long exists = count(new LambdaQueryWrapper<Greenhouse>()
                    .eq(Greenhouse::getSectorId, dto.getSectorId()));
            if (exists > 0) {
                throw new BusinessException(40110, "区域编号已存在");
            }
            existing.setSectorId(dto.getSectorId());
        }
        if (StringUtils.hasText(dto.getCropSpecies())) {
            existing.setCropSpecies(dto.getCropSpecies());
        }
        if (dto.getPlantingDate() != null) {
            existing.setPlantingDate(dto.getPlantingDate());
        }
        if (StringUtils.hasText(dto.getLocation())) {
            existing.setLocation(dto.getLocation());
        }
        if (dto.getArea() != null) {
            existing.setArea(dto.getArea());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            existing.setStatus(dto.getStatus());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGreenhouse(String id) {
        Greenhouse existing = getById(id);
        if (existing == null) {
            throw new BusinessException(40112, "温室不存在");
        }
        // 校验是否关联网格
        Long gridCount = gridMapper.selectCount(
                new LambdaQueryWrapper<Grid>().eq(Grid::getGreenhouseId, id));
        if (gridCount > 0) {
            throw new BusinessException(40113, "温室下存在关联网格，无法删除");
        }
        removeById(id);
    }
}
