package com.cognitivemanager.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure configuration.
 *
 * Architecture decision (see README §2.1): RabbitMQ is used over Apache Kafka because:
 * <ul>
 *   <li>Event volume is low (1–20 events/day per developer).</li>
 *   <li>RabbitMQ provides per-message TTL, dead-letter queues, and sub-10ms delivery
 *       with zero Kafka cluster overhead.</li>
 *   <li>No need for log compaction, partition rebalancing, or consumer groups.</li>
 * </ul>
 *
 * Queue topology:
 * <pre>
 *   [Injection Agent / Teams/Google]
 *          │
 *          ▼
 *   Exchange: cognitive.exchange (topic)
 *          │  routing key: calendar.event.#
 *          ▼
 *   Queue: cognitive.calendar.events
 *          │  (DLQ on failure: cognitive.dead-letter)
 *          ▼
 *   [MeetingEventConsumer → Core Engine]
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    @Value("${cognitive.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${cognitive.rabbitmq.queue.calendar-events}")
    private String calendarEventsQueue;

    @Value("${cognitive.rabbitmq.queue.dead-letter}")
    private String deadLetterQueue;

    @Value("${cognitive.rabbitmq.routing-key.calendar}")
    private String calendarRoutingKey;

    // -------------------------------------------------------------------------
    // Exchange
    // -------------------------------------------------------------------------

    @Bean
    public TopicExchange cognitiveExchange() {
        return ExchangeBuilder
                .topicExchange(exchangeName)
                .durable(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // Dead Letter Queue (catches messages after 3 failed retry attempts)
    // -------------------------------------------------------------------------

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(deadLetterQueue)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("cognitive.dead-letter.exchange");
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(deadLetterQueue);
    }

    // -------------------------------------------------------------------------
    // Main calendar events queue
    // -------------------------------------------------------------------------

    @Bean
    public Queue calendarEventsQueue() {
        return QueueBuilder
                .durable(calendarEventsQueue)
                .withArgument("x-dead-letter-exchange", "cognitive.dead-letter.exchange")
                .withArgument("x-dead-letter-routing-key", deadLetterQueue)
                .withArgument("x-message-ttl", 300_000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Binding calendarEventsBinding() {
        return BindingBuilder
                .bind(calendarEventsQueue())
                .to(cognitiveExchange())
                .with(calendarRoutingKey);
    }

    // -------------------------------------------------------------------------
    // JSON message converter — enables automatic serialization/deserialization
    // -------------------------------------------------------------------------

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}
