package com.agriculture.entity;

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
 * 摄像头表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("camera")
public class Camera implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 摄像头UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 摄像头名称
     */
    @TableField("name")
    private String name;

    /**
     * RTSP流地址
     */
    @TableField("rtsp_url")
    private String rtspUrl;

    /**
     * 经度
     */
    @TableField("location_x")
    private BigDecimal locationX;

    /**
     * 纬度
     */
    @TableField("location_y")
    private BigDecimal locationY;

    /**
     * 朝向角度
     */
    @TableField("direction")
    private BigDecimal direction;

    /**
     * 状态: ONLINE/OFFLINE/FAULT
     */
    @TableField("status")
    private String status;

    /**
     * 最后抓拍时间
     */
    @TableField("last_frame_at")
    private LocalDateTime lastFrameAt;

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
