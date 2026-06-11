package com.agriculture.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 网格信息返回VO
 */
@Data
public class GridVO {

    /**
     * 网格UUID
     */
    private String id;

    /**
     * 网格编号(A1/B3等)
     */
    private String label;

    /**
     * 大棚ID
     */
    private String greenhouseId;

    /**
     * 多边形坐标点JSON
     */
    private String polygonCoords;

    /**
     * 面积(平方米)
     */
    private BigDecimal areaM2;

    /**
     * 作物类型
     */
    private String cropType;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
