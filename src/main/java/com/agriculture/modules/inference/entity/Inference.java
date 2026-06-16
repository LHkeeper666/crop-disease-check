package com.agriculture.modules.inference.entity;

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
 * 识别结果表 (一行对应一张图片的一次推理)
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("inference")
public class Inference implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 识别UUID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 关联上报ID
     */
    @TableField("report_id")
    private String reportId;

    /**
     * 所属企业ID
     */
    @TableField("company_id")
    private String companyId;

    /**
     * 病害ID数组 [0,3,15]，JSON格式
     */
    @TableField("disease_ids")
    private String diseaseIds;

    /**
     * 虫害ID数组 [22,45]，JSON格式
     */
    @TableField("pest_ids")
    private String pestIds;

    /**
     * 完整检测结果数组，JSON格式
     * 每项含: pipeline, class_id, class_name, name_cn, confidence, bbox
     */
    @TableField("detections")
    private String detections;

    /**
     * 标注图存储路径/URL
     */
    @TableField("annotated_image_url")
    private String annotatedImageUrl;

    /**
     * 双模型总推理耗时(ms)
     */
    @TableField("total_elapsed_ms")
    private BigDecimal totalElapsedMs;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 数据来源: REPORT(用户上报) / CAMERA(摄像头自动检测)
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 来源摄像头ID（仅 source_type=CAMERA 时有值）
     */
    @TableField("camera_id")
    private String cameraId;

    /**
     * 关联网格标签，逗号分隔（冗余存储，避免每次 JOIN）
     */
    @TableField("grid_labels")
    private String gridLabels;
}
