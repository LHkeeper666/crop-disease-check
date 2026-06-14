package com.agriculture.modules.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 摄像头实时状态VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraStatusVO {

    private String id;
    private String status;
    private String lastFrameAt;
    private String httpUrl;
    private ConnectionInfo connectionInfo;
    private FrameInfo frameInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionInfo {
        private String protocol;
        private String transport;
        private long uptime;
        private int retryCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrameInfo {
        private int width;
        private int height;
        private int fps;
        private String codec;
    }
}
