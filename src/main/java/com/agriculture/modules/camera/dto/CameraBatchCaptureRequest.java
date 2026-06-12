package com.agriculture.modules.camera.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量抓拍请求DTO
 */
@Data
public class CameraBatchCaptureRequest {

    @NotEmpty(message = "摄像头ID列表不能为空")
    @Size(max = 10, message = "摄像头ID列表最多10个")
    private List<String> cameraIds;

    private Boolean submitInference = true;
}
