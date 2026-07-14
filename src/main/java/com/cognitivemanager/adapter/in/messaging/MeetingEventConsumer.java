package com.cognitivemanager.adapter.in.messaging;

import com.cognitivemanager.domain.model.Meeting;
import com.cognitivemanager.port.in.ProcessMeetingEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * DESIGN PATTERN: Adapter Pattern — Inbound (Driver) Adapter
 *
 * Simulates the Injection Agent role in the Event-Driven pipeline (§1, Visão Macro).
 * Listens to the RabbitMQ {@code cognitive.calendar.events} queue for calendar
 * mutation events from external systems (Teams / Google Calendar / Outlook).
 *
 * Responsibilities:
 * <ol>
 *   <li>Deserialize the raw {@link MeetingEventPayload} from JSON.</li>
 *   <li>Validate the payload has the minimum required fields.</li>
 *   <li>Map the external payload to the rich domain {@link Meeting} model.</li>
 *   <li>Delegate to the primary port ({@link ProcessMeetingEventUseCase}).</li>
 * </ol>
 *
 * This class knows about RabbitMQ and JSON. The domain knows nothing about either.
 * That boundary is the Adapter Pattern.
 *
 * RF01: Consumes real-time calendar events and triggers the Core Engine pipeline.
 */
@Component
public class MeetingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MeetingEventConsumer.class);

    private final ProcessMeetingEventUseCase processMeetingEventUseCase;

    public MeetingEventConsumer(ProcessMeetingEventUseCase processMeetingEventUseCase) {
        this.processMeetingEventUseCase = processMeetingEventUseCase;
    }

    /**
     * Listens to the calendar events queue and routes events to the appropriate use case.
     *
     * Spring AMQP deserializes the JSON body into {@link MeetingEventPayload} automatically
     * using the {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}
     * configured in {@link com.cognitivemanager.config.RabbitMQConfig}.
     *
     * @param payload The deserialized event payload from RabbitMQ
     */
    @RabbitListener(queues = "${cognitive.rabbitmq.queue.calendar-events}")
    public void onMeetingEvent(MeetingEventPayload payload) {
        log.info("RabbitMQ event received: {}", payload);

        try {
            validatePayload(payload);

            if (payload.isCancellation()) {
                // Route to cancellation handler — frees the slot and re-allocates
                processMeetingEventUseCase.processMeetingCancellation(
                        payload.getEventId(),
                        payload.getStartTime().toLocalDate(),
                        payload.getDeveloperId());
            } else {
                // Route to creation/update handler
                Meeting meeting = adaptPayloadToDomain(payload);
                processMeetingEventUseCase.processMeetingEvent(meeting, payload.getDeveloperId());
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid meeting event payload: {} — {}", payload.getEventId(), e.getMessage());
            // Do not rethrow: invalid payloads go to dead-letter queue automatically via AMQP config
        } catch (Exception e) {
            log.error("Failed to process meeting event: {}", payload.getEventId(), e);
            // Rethrow to trigger Spring AMQP retry (3 attempts configured in application.yml)
            throw new RuntimeException("Meeting event processing failed", e);
        }
    }

    /**
     * DESIGN PATTERN: Adapter Pattern — mapping external DTO to domain model.
     *
     * Converts the external calendar system's flat payload into the rich, validated
     * {@link Meeting} domain object. All type coercions happen here.
     */
    private Meeting adaptPayloadToDomain(MeetingEventPayload payload) {
        return new Meeting(
                UUID.fromString(payload.getEventId()),     // ID: String → UUID
                payload.getTitle(),
                payload.getStartTime(),
                payload.getEndTime(),
                payload.isUrgent(),                        // Priority: String → boolean
                payload.getSource() != null ? payload.getSource() : "UNKNOWN"
        );
    }

    private void validatePayload(MeetingEventPayload payload) {
        if (payload.getEventId() == null || payload.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (payload.getDeveloperId() == null || payload.getDeveloperId().isBlank()) {
            throw new IllegalArgumentException("developerId is required");
        }
        if (!payload.isCancellation()) {
            if (payload.getTitle() == null || payload.getTitle().isBlank()) {
                throw new IllegalArgumentException("title is required for non-cancellation events");
            }
            if (payload.getStartTime() == null || payload.getEndTime() == null) {
                throw new IllegalArgumentException("startTime and endTime are required");
            }
            if (!payload.getEndTime().isAfter(payload.getStartTime())) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
        }
    }
}
