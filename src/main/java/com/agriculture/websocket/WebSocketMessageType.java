package com.agriculture.websocket;

/**
 * WebSocket 消息类型常量
 */
public final class WebSocketMessageType {

    private WebSocketMessageType() {
        // 工具类，禁止实例化
    }

    /**
     * 推理结果推送
     */
    public static final String INFERENCE_RESULT = "INFERENCE_RESULT";

    /**
     * 工单状态变更推送
     */
    public static final String WORKORDER_STATUS_CHANGE = "WORKORDER_STATUS_CHANGE";

    /**
     * 热力图数据更新推送
     */
    public static final String HEATMAP_UPDATE = "HEATMAP_UPDATE";

    /**
     * 巡检任务状态推送
     */
    public static final String INSPECTION_STATUS = "INSPECTION_STATUS";
}
