package com.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

/**
 * 新增网格请求DTO
 */
@Data
public class GridCreateDTO {

    /**
     * 网格编号(A1/B3等)
     */
    @NotBlank(message = "网格编号不能为空")
    private String label;

    /**
     * 大棚ID
     */
    private String greenhouseId;

    /**
     * 多边形坐标点 [{"x":0,"y":0}, ...]
     */
    private List<CoordPoint> polygonCoords;

    /**
     * 作物类型
     */
    private String cropType;

    /**
     * 坐标点内部类
     */
    @Data
    public static class CoordPoint {
        private Double x;
        private Double y;
    }
}
