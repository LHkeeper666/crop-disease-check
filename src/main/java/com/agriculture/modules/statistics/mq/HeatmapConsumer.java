package com.agriculture.modules.statistics.mq;

import com.agriculture.common.config.RabbitMQConfig;
import com.agriculture.common.mq.event.DetectionEvent;
import com.agriculture.common.websocket.WebSocketService;
import com.agriculture.modules.statistics.service.HeatmapService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 热力图消费者 —— 消费检测事件，增量更新受影响网格的热力图分数并推送 WebSocket
 */
@Slf4j
@Component
public class HeatmapConsumer {

    @Resource
    private HeatmapService heatmapService;

    @Resource
    private WebSocketService webSocketService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_HEATMAP, concurrency = "1-2")
    public void onDetection(DetectionEvent event, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("消费检测事件，更新热力图: grids={}", event.getGridLabels());
        try {
            for (String gridLabel : event.getGridLabels()) {
                double score = heatmapService.recalculateGridScore(gridLabel, event.getCompanyId());
                Map<String, Object> wsData = new HashMap<>();
                wsData.put("gridId", gridLabel);
                wsData.put("gridLabel", gridLabel);
                wsData.put("score", score);
                wsData.put("updatedAt", LocalDateTime.now().toString());
                webSocketService.sendHeatmapUpdate(wsData);
                log.debug("热力图已更新: grid={}, score={}", gridLabel, score);
            }
            channel.basicAck(deliveryTag, false);
            log.info("热力图更新完成: grids={}", event.getGridLabels());
        } catch (Exception e) {
            log.error("热力图更新失败，消息进入死信队列: inferenceId={}", event.getInferenceId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
