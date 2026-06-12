package com.agriculture.modules.camera.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
     * RTSP子码流地址
     */
    @TableField("rtsp_url_sub")
    private String rtspUrlSub;

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
     * 抓拍分辨率
     */
    @TableField("capture_resolution")
    private String captureResolution;

    /**
     * 抓拍JPEG质量(1-100)
     */
    @TableField("capture_quality")
    private Integer captureQuality;

    /**
     * 断流重连间隔(秒)
     */
    @TableField("reconnect_interval")
    private Integer reconnectInterval;

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
     * 最近一次在线时间
     */
    @TableField("last_online_at")
    private LocalDateTime lastOnlineAt;

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
     * 覆盖网格ID列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<String> coverageGrids;
}
