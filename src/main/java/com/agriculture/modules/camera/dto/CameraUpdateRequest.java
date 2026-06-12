package com.agriculture.modules.camera.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 修改摄像头请求DTO
 */
@Data
public class CameraUpdateRequest {

    private String name;

    private String rtspUrl;

    private String rtspUrlSub;

    private BigDecimal locationX;

    private BigDecimal locationY;

    private BigDecimal direction;

    private List<String> coverageGrids;

    private String captureResolution;

    private Integer captureQuality;

    private Integer reconnectInterval;
}
