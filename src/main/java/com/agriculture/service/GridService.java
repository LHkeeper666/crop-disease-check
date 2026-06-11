package com.agriculture.service;

import com.agriculture.dto.GridCreateDTO;
import com.agriculture.dto.GridUpdateDTO;
import com.agriculture.entity.Grid;
import com.agriculture.vo.GridVO;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * 网格区域服务接口
 */
public interface GridService extends IService<Grid> {

    /**
     * 获取网格列表
     */
    List<GridVO> listGrids(String greenhouseId);

    /**
     * 新增网格
     */
    String createGrid(GridCreateDTO dto);

    /**
     * 修改网格
     */
    void updateGrid(String id, GridUpdateDTO dto);

    /**
     * 删除网格
     */
    void deleteGrid(String id);
}
