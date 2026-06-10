package com.agriculture.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 摄像头实时识别响应DTO
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
    private InferenceResult inference;
    private WorkOrderInfo workOrder;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InferenceResult {
        private ModelResult disease;
        private ModelResult pest;
        private String annotatedImage;
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
