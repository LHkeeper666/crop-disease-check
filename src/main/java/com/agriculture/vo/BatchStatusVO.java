package com.agriculture.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BatchStatusVO {

    private List<CameraStatusItem> statuses;
    private StatusSummary summary;

    @Data
    @Builder
    public static class CameraStatusItem {
        private String id;
        private String name;
        private String status;
        private LocalDateTime lastFrameAt;
        private String streamUrl;
    }

    @Data
    @Builder
    public static class StatusSummary {
        private int total;
        private int online;
        private int offline;
        private int fault;
    }
}
