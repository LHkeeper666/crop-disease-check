package com.agriculture.modules.camera.dto;

import lombok.Data;

/**
 * 实时监测模式请求DTO
 */
@Data
public class CameraMonitorRequest {

    /**
     * 是否启用实时监测
     */
    private Boolean enabled = true;

    /**
     * 抽帧间隔(秒)，最小3秒，默认5秒
     */
    private Integer intervalSeconds = 5;

    /**
     * 置信度阈值
     */
    private Float confidence = 0.5f;

    /**
     * 是否使用子码流
     */
    private Boolean useSubStream = false;
}
