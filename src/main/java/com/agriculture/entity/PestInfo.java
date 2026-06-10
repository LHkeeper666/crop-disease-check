package com.agriculture.entity;

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
 * 病虫害知识库
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
     * 病虫害UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 病虫害名称
     */
    @TableField("pest_name")
    private String pestName;

    /**
     * 类型: DISEASE/PEST/WEED
     */
    @TableField("pest_type")
    private String pestType;

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
