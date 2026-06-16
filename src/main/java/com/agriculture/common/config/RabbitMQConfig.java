package com.agriculture.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：Topic Exchange + 双队列（工单生成 / 热力图更新）+ 死信
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "agri.detect.exchange";
    public static final String DLX_EXCHANGE = "agri.detect.dlx";

    public static final String QUEUE_WORKORDER = "workorder.create";
    public static final String QUEUE_HEATMAP = "heatmap.update";
    public static final String QUEUE_DLQ = "detect.dlq";

    // ==================== Exchange ====================

    @Bean
    public TopicExchange detectExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    // ==================== Queue ====================

    @Bean
    public Queue workorderQueue() {
        return QueueBuilder.durable(QUEUE_WORKORDER)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue heatmapQueue() {
        return QueueBuilder.durable(QUEUE_HEATMAP)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    // ==================== Binding ====================

    @Bean
    public Binding workorderBinding(Queue workorderQueue, TopicExchange detectExchange) {
        return BindingBuilder.bind(workorderQueue).to(detectExchange).with("detect.*");
    }

    @Bean
    public Binding heatmapBinding(Queue heatmapQueue, TopicExchange detectExchange) {
        return BindingBuilder.bind(heatmapQueue).to(detectExchange).with("detect.*");
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(QUEUE_DLQ);
    }

    // ==================== 序列化 / 模板 ====================

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                        .warn("MQ消息投递失败: cause={}", cause);
            }
        });
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        return factory;
    }
}
