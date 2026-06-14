package com.agriculture.modules.grid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 网格区域表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("grid")
public class Grid implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 网格UUID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 网格编号(A1/B3等)
     */
    @TableField("label")
    private String label;

    /**
     * 大棚ID
     */
    @TableField("greenhouse_id")
    private String greenhouseId;

    /**
     * 多边形坐标点
     */
    @TableField("polygon_coords")
    private String polygonCoords;

    /**
     * 面积(平方米)
     */
    @TableField("area_m2")
    private BigDecimal areaM2;

    /**
     * 作物类型
     */
    @TableField("crop_type")
    private String cropType;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除
     */
    @TableField("deleted")
    private Byte deleted;
}
