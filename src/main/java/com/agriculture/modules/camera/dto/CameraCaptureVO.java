package com.agriculture.modules.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手动抓拍响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraCaptureVO {

    private String imageUrl;
    private String capturedAt;
    private String inferenceTaskId;
}
