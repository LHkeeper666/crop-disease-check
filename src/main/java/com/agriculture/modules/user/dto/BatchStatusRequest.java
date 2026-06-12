package com.agriculture.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class BatchStatusRequest {

    @NotNull(message = "摄像头ID列表不能为空")
    @Size(max = 50, message = "批量状态查询最多50个摄像头")
    private List<String> cameraIds;
}
