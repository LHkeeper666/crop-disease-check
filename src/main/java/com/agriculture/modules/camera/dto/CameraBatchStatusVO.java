package com.agriculture.modules.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量状态查询响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraBatchStatusVO {

    private List<CameraStatusItem> statuses;
    private StatusSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CameraStatusItem {
        private String id;
        private String name;
        private String status;
        private String lastFrameAt;
        private String httpUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusSummary {
        private int total;
        private int online;
        private int offline;
        private int fault;
    }
}
