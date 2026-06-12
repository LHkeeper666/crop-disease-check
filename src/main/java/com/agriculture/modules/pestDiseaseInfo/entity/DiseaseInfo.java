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
 * 病害信息表 (id 对应 YOLOv8 病害模型 class index, 0-37)
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("disease_info")
public class DiseaseInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 病害ID(模型class index)
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 病害名称(英文)
     */
    @TableField("disease_name")
    private String diseaseName;

    /**
     * 病害名称(中文)
     */
    @TableField("name_cn")
    private String nameCn;

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
