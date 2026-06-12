package com.agriculture.modules.camera.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量状态查询请求DTO
 */
@Data
public class CameraBatchStatusRequest {

    @NotEmpty(message = "摄像头ID列表不能为空")
    @Size(max = 50, message = "摄像头ID列表最多50个")
    private List<String> cameraIds;
}
