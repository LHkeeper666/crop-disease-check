package com.agriculture.modules.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 温室/大棚表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-10
 */
@Getter
@Setter
@TableName("greenhouse")
public class Greenhouse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 温室UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 区域编号(如GH-A1)
     */
    @TableField("sector_id")
    private String sectorId;

    /**
     * 作物种类
     */
    @TableField("crop_species")
    private String cropSpecies;

    /**
     * 定植日期
     */
    @TableField("planting_date")
    private LocalDate plantingDate;

    /**
     * 地理位置坐标
     */
    @TableField("location")
    private String location;

    /**
     * 面积(m²)
     */
    @TableField("area")
    private BigDecimal area;

    /**
     * 状态: ACTIVE/INACTIVE/MAINTENANCE
     */
    @TableField("status")
    private String status;

    /**
     * 所属企业ID
     */
    @TableField("company_id")
    private String companyId;

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

    /**
     * 关联网格数（非数据库字段，详情接口使用）
     */
    @TableField(exist = false)
    private Integer gridCount;

    /**
     * 关联摄像头数（非数据库字段，详情接口使用）
     */
    @TableField(exist = false)
    private Integer cameraCount;
}
