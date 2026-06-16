package com.agriculture.common.mq.event;

import lombok.Data;

import java.util.List;

/**
 * 检测事件消息体 —— 摄像头检测/用户上报 持久化后发送到 RabbitMQ
 */
@Data
public class DetectionEvent {

    /** inference 表主键 */
    private String inferenceId;

    /** 来源摄像头ID（REPORT 时为 null） */
    private String cameraId;

    /** CAMERA / REPORT */
    private String sourceType;

    /** 所属企业ID */
    private String companyId;

    /** 关联网格标签列表 */
    private List<String> gridLabels;

    /** 检测到的病虫害列表 */
    private List<PestDetection> detections;

    @Data
    public static class PestDetection {
        private int classId;
        private String className;
        private String nameCn;
        private double confidence;
        /** disease / pest */
        private String type;
    }
}
