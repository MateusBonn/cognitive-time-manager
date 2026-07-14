package com.cognitivemanager.adapter.in.rest;

import com.cognitivemanager.adapter.in.rest.dto.InterruptionDecisionRequest;
import com.cognitivemanager.adapter.in.rest.dto.ScheduleDayResponse;
import com.cognitivemanager.domain.model.InterruptionResult;
import com.cognitivemanager.domain.model.Meeting;
import com.cognitivemanager.port.in.AllocateTimeUseCase;
import com.cognitivemanager.port.in.HandleInterruptionUseCase;
import com.cognitivemanager.port.in.HandleInterruptionUseCase.InterruptionDecision;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * REST adapter — exposes the cognitive time management operations over HTTP.
 *
 * DESIGN PATTERN: Adapter Pattern — Inbound (Driver) Adapter
 * Translates HTTP requests into use case calls. The controller knows nothing
 * about the domain beyond the port interfaces and DTOs.
 *
 * All endpoints are under {@code /api/v1/schedule}.
 */
@RestController
@RequestMapping("/api/v1/schedule")
public class TimeManagementController {

    private static final LocalTime DEFAULT_WORKDAY_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORKDAY_END = LocalTime.of(18, 0);

    private final AllocateTimeUseCase allocateTimeUseCase;
    private final HandleInterruptionUseCase handleInterruptionUseCase;

    public TimeManagementController(
            AllocateTimeUseCase allocateTimeUseCase,
            HandleInterruptionUseCase handleInterruptionUseCase) {
        this.allocateTimeUseCase = allocateTimeUseCase;
        this.handleInterruptionUseCase = handleInterruptionUseCase;
    }

    // -------------------------------------------------------------------------
    // Schedule endpoints
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/schedule/{developerId}/{date}
     * Retrieves (or generates on-demand) the cognitive schedule for a given date.
     *
     * Example: GET /api/v1/schedule/dev-42/2026-07-14
     */
    @GetMapping("/{developerId}/{date}")
    public ResponseEntity<ScheduleDayResponse> getSchedule(
            @PathVariable String developerId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDateTime workdayStart = date.atTime(DEFAULT_WORKDAY_START);
        LocalDateTime workdayEnd = date.atTime(DEFAULT_WORKDAY_END);

        var day = allocateTimeUseCase.allocateDay(developerId, date, workdayStart, workdayEnd);
        return ResponseEntity.ok(ScheduleDayResponse.from(day));
    }

    /**
     * POST /api/v1/schedule/{developerId}/reallocate?date=2026-07-14
     * Manually triggers a full re-allocation for the target date.
     * If {@code date} is omitted, defaults to today.
     */
    @PostMapping("/{developerId}/reallocate")
    public ResponseEntity<ScheduleDayResponse> reallocate(
            @PathVariable String developerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime workdayStart = targetDate.atTime(DEFAULT_WORKDAY_START);
        LocalDateTime workdayEnd = targetDate.atTime(DEFAULT_WORKDAY_END);

        var day = allocateTimeUseCase.reallocateDay(developerId, targetDate, workdayStart, workdayEnd);
        return ResponseEntity.ok(ScheduleDayResponse.from(day));
    }

    // -------------------------------------------------------------------------
    // Interruption endpoints
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/schedule/{developerId}/interrupt
     * Simulates an urgent meeting interruption for a developer currently in Deep Work.
     * Used for testing and manual injection when the RabbitMQ source is unavailable.
     *
     * Request body:
     * <pre>
     * {
     *   "title":      "Sync urgente com PM",
     *   "start_time": "2026-07-14T10:30:00",
     *   "end_time":   "2026-07-14T11:00:00"
     * }
     * </pre>
     */
    @PostMapping("/{developerId}/interrupt")
    public ResponseEntity<InterruptionResult> simulateInterruption(
            @PathVariable String developerId,
            @RequestBody UrgentMeetingRequest request) {

        Meeting urgentMeeting = new Meeting(
                UUID.randomUUID(),
                request.title(),
                request.startTime(),
                request.endTime(),
                true
        );

        InterruptionResult result = handleInterruptionUseCase.handleInterruption(
                developerId, urgentMeeting, LocalDateTime.now());

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/schedule/{developerId}/decision
     * Applies the developer's explicit choice for a DECISION_REQUIRED interruption.
     *
     * Request body:
     * <pre>
     * {
     *   "meeting_id":       "550e8400-e29b-41d4-a716-446655440000",
     *   "switch_to_shallow": false
     * }
     * </pre>
     * {@code switch_to_shallow: true}  → Option A (Shallow Work now)
     * {@code switch_to_shallow: false} → Option B (continue Deep Work)
     */
    @PostMapping("/{developerId}/decision")
    public ResponseEntity<ScheduleDayResponse> applyDecision(
            @PathVariable String developerId,
            @RequestBody InterruptionDecisionRequest request) {

        InterruptionDecision decision = request.switchToShallow()
                ? InterruptionDecision.SWITCH_TO_SHALLOW
                : InterruptionDecision.PERSIST_DEEP_WORK;

        var updated = handleInterruptionUseCase.applyInterruptionDecision(
                developerId, decision, request.meetingId());

        return ResponseEntity.ok(ScheduleDayResponse.from(updated));
    }

    // -------------------------------------------------------------------------
    // Request records
    // -------------------------------------------------------------------------

    record UrgentMeetingRequest(
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {}
}
