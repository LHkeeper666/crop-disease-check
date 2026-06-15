package com.agriculture.modules.camera.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增摄像头请求DTO
 */
@Data
public class CameraCreateRequest {

    @NotBlank(message = "摄像头名称不能为空")
    private String name;

    @NotBlank(message = "RTSP地址不能为空")
    @Pattern(regexp = "^rtsp://.*", message = "RTSP地址格式不正确")
    private String rtspUrl;

    private String rtspUrlSub;

    private BigDecimal locationX;

    private BigDecimal locationY;

    private BigDecimal direction;

    private List<String> coverageGrids;

    private String captureResolution;

    private Integer captureQuality = 85;

    private Integer reconnectInterval = 30;
}
