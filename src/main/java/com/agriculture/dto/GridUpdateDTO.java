package com.agriculture.dto;

import java.util.List;
import lombok.Data;

/**
 * 修改网格请求DTO
 */
@Data
public class GridUpdateDTO {

    /**
     * 网格编号(A1/B3等)
     */
    private String label;

    /**
     * 大棚ID
     */
    private String greenhouseId;

    /**
     * 多边形坐标点
     */
    private List<GridCreateDTO.CoordPoint> polygonCoords;

    /**
     * 作物类型
     */
    private String cropType;
}
