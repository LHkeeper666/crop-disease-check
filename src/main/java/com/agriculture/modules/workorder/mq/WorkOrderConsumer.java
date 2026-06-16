package com.agriculture.modules.workorder.mq;

import com.agriculture.common.mq.event.DetectionEvent;
import com.agriculture.common.config.RabbitMQConfig;
import com.agriculture.modules.workorder.service.WorkOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * 工单消费者 —— 消费检测事件，生成智能工单
 */
@Slf4j
@Component
public class WorkOrderConsumer {

    @Resource
    private WorkOrderService workOrderService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_WORKORDER, concurrency = "2-4")
    public void onDetection(DetectionEvent event, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("消费检测事件，生成工单: inferenceId={}, grids={}", event.getInferenceId(), event.getGridLabels());
        try {
            workOrderService.createFromDetectionEvent(event);
            channel.basicAck(deliveryTag, false);
            log.info("工单生成完成: inferenceId={}", event.getInferenceId());
        } catch (Exception e) {
            log.error("工单生成失败，消息进入死信队列: inferenceId={}", event.getInferenceId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
