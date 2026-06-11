package com.agriculture.modules.pestDiseaseInfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 虫害信息表 (id 对应 YOLOv8 虫害模型 class index, 0-101)
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("pest_info")
public class PestInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 虫害ID(模型class index)
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 虫害名称
     */
    @TableField("pest_name")
    private String pestName;

    /**
     * 描述
     */
    @TableField("description")
    private String description;

    /**
     * 发生条件
     */
    @TableField("conditions")
    private String conditions;

    /**
     * 防治方法
     */
    @TableField("prevention")
    private String prevention;

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
