package com.cognitivemanager.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * DESIGN PATTERN: Adapter Pattern — External Data Transfer Object
 *
 * Raw payload structure from external calendar systems (Teams / Google Calendar /
 * Outlook). This class intentionally uses snake_case field names matching the
 * external API contracts.
 *
 * This is NOT a domain object. It carries zero business logic. Its only purpose
 * is to be deserialized from JSON by Jackson and then mapped to the rich domain
 * {@link com.cognitivemanager.domain.model.Meeting} object inside
 * {@link MeetingEventConsumer}.
 *
 * The separation between payload (infrastructure) and domain model (domain) is
 * the Adapter Pattern boundary. If Teams changes its payload schema, only this
 * class changes — the domain model stays untouched.
 */
public class MeetingEventPayload {

    /**
     * External event ID — maps to {@link com.cognitivemanager.domain.model.Meeting#getId()}.
     * Must be a valid UUID string.
     */
    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("title")
    private String title;

    /** ISO-8601 datetime string deserialized by Jackson's JSR-310 module */
    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    private LocalDateTime endTime;

    /**
     * Source calendar system. One of: "TEAMS", "GOOGLE", "OUTLOOK".
     * Used for logging and potential source-specific business rules in V2.
     */
    @JsonProperty("source")
    private String source;

    /**
     * Meeting priority. "URGENT" triggers the three-scenario interruption evaluation.
     * Any other value is treated as normal (standard re-allocation).
     */
    @JsonProperty("priority")
    private String priority;

    @JsonProperty("developer_id")
    private String developerId;

    /**
     * Event mutation type. One of: "CREATED", "UPDATED", "CANCELLED".
     * Drives the routing in {@link MeetingEventConsumer}.
     */
    @JsonProperty("event_type")
    private String eventType;

    // -------------------------------------------------------------------------
    // Convenience predicates
    // -------------------------------------------------------------------------

    public boolean isUrgent() {
        return "URGENT".equalsIgnoreCase(priority);
    }

    public boolean isCancellation() {
        return "CANCELLED".equalsIgnoreCase(eventType);
    }

    // -------------------------------------------------------------------------
    // Getters and setters (Jackson requires these)
    // -------------------------------------------------------------------------

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getDeveloperId() { return developerId; }
    public void setDeveloperId(String developerId) { this.developerId = developerId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    @Override
    public String toString() {
        return String.format("MeetingEventPayload{eventId='%s', type=%s, priority=%s, developer=%s, start=%s}",
                eventId, eventType, priority, developerId, startTime);
    }
}
