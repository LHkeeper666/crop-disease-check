package com.agriculture.modules.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 摄像头检测响应DTO
 * 注意：不返回标注图(base64)，只返回结构化的检测框坐标
 * 前端通过Canvas在MJPEG视频上绘制检测框
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraDetectResponse {

    private String cameraId;
    private String cameraName;
    private String captureTime;
    private String captureUrl;

    /**
     * 抽帧图片宽度（用于前端坐标映射）
     */
    private Integer imageWidth;

    /**
     * 抽帧图片高度（用于前端坐标映射）
     */
    private Integer imageHeight;

    private InferenceResult inference;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InferenceResult {
        private ModelResult disease;
        private ModelResult pest;
        private Double totalElapsedMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelResult {
        private List<DetectionItem> detections;
        private Integer count;
        private Double elapsedMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectionItem {
        private Integer classId;
        private String className;
        private String nameCn;
        private Double confidence;
        private BBox bbox;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BBox {
        private Integer x;
        private Integer y;
        private Integer width;
        private Integer height;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkOrderInfo {
        private Boolean created;
        private String workOrderId;
        private String severity;
    }
}
