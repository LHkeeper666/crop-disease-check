package com.agriculture.modules.camera.dto;

import lombok.Data;

/**
 * 手动抓拍请求DTO
 */
@Data
public class CameraCaptureRequest {

    private String resolution;

    private Integer quality;

    private Boolean submitInference = true;
}
