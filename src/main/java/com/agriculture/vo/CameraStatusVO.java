package com.agriculture.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CameraStatusVO {

    private String id;
    private String status;
    private LocalDateTime lastFrameAt;
    private String streamUrl;
    private ConnectionInfo connectionInfo;
    private FrameInfo frameInfo;

    @Data
    @Builder
    public static class ConnectionInfo {
        private String protocol;
        private String transport;
        private long uptime;
        private int retryCount;
    }

    @Data
    @Builder
    public static class FrameInfo {
        private int width;
        private int height;
        private int fps;
        private String codec;
    }
}
