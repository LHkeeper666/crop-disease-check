package com.agriculture.modules.camera.dto;

import lombok.Data;

/**
 * 抓拍配置更新请求DTO
 */
@Data
public class CameraCaptureConfigRequest {

    private String captureResolution;

    private Integer captureQuality;

    private Integer reconnectInterval;
}
