package com.agriculture.service;

import com.agriculture.dto.GreenhouseDTO;
import com.agriculture.entity.Greenhouse;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 温室/大棚表 服务类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-10
 */
public interface GreenhouseService extends IService<Greenhouse> {

    /**
     * 温室列表查询
     */
    IPage<Greenhouse> listGreenhouses(String status, String keyword, int page, int size);

    /**
     * 温室详情（含 gridCount、cameraCount）
     */
    Greenhouse getGreenhouseDetail(String id);

    /**
     * 新增温室
     */
    String createGreenhouse(GreenhouseDTO dto, String companyId);

    /**
     * 修改温室
     */
    void updateGreenhouse(String id, GreenhouseDTO dto);

    /**
     * 删除温室
     */
    void deleteGreenhouse(String id);
}
