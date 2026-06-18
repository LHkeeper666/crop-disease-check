package com.agriculture.modules.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("annotation_box")
public class AnnotationBox implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("annotation_id")
    private Long annotationId;

    @TableField("class_id")
    private Integer classId;

    @TableField("class_name")
    private String className;

    @TableField("name_cn")
    private String nameCn;

    @TableField("x")
    private Double x;

    @TableField("y")
    private Double y;

    @TableField("width")
    private Double width;

    @TableField("height")
    private Double height;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
