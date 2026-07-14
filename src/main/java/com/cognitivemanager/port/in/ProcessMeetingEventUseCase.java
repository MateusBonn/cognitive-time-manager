package com.cognitivemanager.port.in;

import com.cognitivemanager.domain.model.Meeting;

import java.time.LocalDate;

/**
 * PRIMARY PORT (Driver / Inbound Use Case)
 *
 * Entry point for calendar event ingestion — the first step in the event-driven pipeline.
 *
 * RF01: Handles creation and mutation events from external calendar systems
 *       (Microsoft Teams, Google Calendar, Outlook).
 *
 * Called by the RabbitMQ consumer adapter ({@link com.cognitivemanager.adapter.in.messaging.MeetingEventConsumer})
 * after it translates the raw payload into a domain {@link Meeting} object.
 */
public interface ProcessMeetingEventUseCase {

    /**
     * Processes a new or updated meeting event.
     * Adds the meeting to the developer's schedule and triggers re-allocation
     * of all affected time windows.
     *
     * If the meeting is:
     * <ul>
     *   <li>Scheduled for today and marked urgent → triggers interruption handling.</li>
     *   <li>Scheduled for today (non-urgent) or a future date → triggers re-allocation.</li>
     * </ul>
     *
     * @param meeting     The meeting to integrate into the schedule
     * @param developerId Developer whose calendar changed
     */
    void processMeetingEvent(Meeting meeting, String developerId);

    /**
     * Processes a meeting cancellation event.
     * Removes the cancelled meeting from the schedule and reclaims the freed
     * time slot for potential Deep Work re-allocation.
     *
     * @param meetingId   Unique ID of the cancelled meeting
     * @param date        Date of the cancelled meeting (for repository lookup)
     * @param developerId Developer whose meeting was cancelled
     */
    void processMeetingCancellation(String meetingId, LocalDate date, String developerId);
}
