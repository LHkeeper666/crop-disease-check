package com.agriculture.modules.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量抓拍响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraBatchCaptureVO {

    private List<BatchCaptureItem> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchCaptureItem {
        private String cameraId;
        private String cameraName;
        private boolean success;
        private String imageUrl;
        private String capturedAt;
        private String inferenceTaskId;
        private String error;
    }
}
