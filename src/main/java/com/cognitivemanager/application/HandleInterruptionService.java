package com.cognitivemanager.application;

import com.cognitivemanager.domain.constants.EngineConstants;
import com.cognitivemanager.domain.engine.InterruptionHandler;
import com.cognitivemanager.domain.model.*;
import com.cognitivemanager.domain.state.*;
import com.cognitivemanager.port.in.HandleInterruptionUseCase;
import com.cognitivemanager.port.output.NotificationPort;
import com.cognitivemanager.port.output.ScheduleDayRepository;
import com.cognitivemanager.port.output.TimeBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application service implementing {@link HandleInterruptionUseCase}.
 *
 * Coordinates the interruption evaluation flow:
 * <ol>
 *   <li>Load accumulated Deep Work minutes for the day.</li>
 *   <li>Reconstruct the current {@link DeveloperState}.</li>
 *   <li>Delegate to {@link InterruptionHandler} (which delegates to the state).</li>
 *   <li>Apply auto-handled scenarios (EMERGENCY, SAFE_AUTO_TRUNCATION) immediately.</li>
 *   <li>Notify the developer via WebSocket for all scenarios.</li>
 * </ol>
 */
@Service
@Transactional
public class HandleInterruptionService implements HandleInterruptionUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleInterruptionService.class);

    private final InterruptionHandler interruptionHandler;
    private final ScheduleDayRepository scheduleDayRepository;
    private final TimeBlockRepository timeBlockRepository;
    private final NotificationPort notificationPort;

    public HandleInterruptionService(
            ScheduleDayRepository scheduleDayRepository,
            TimeBlockRepository timeBlockRepository,
            NotificationPort notificationPort) {
        this.interruptionHandler = new InterruptionHandler();
        this.scheduleDayRepository = Objects.requireNonNull(scheduleDayRepository);
        this.timeBlockRepository = Objects.requireNonNull(timeBlockRepository);
        this.notificationPort = Objects.requireNonNull(notificationPort);
    }

    @Override
    public InterruptionResult handleInterruption(
            String developerId,
            Meeting urgentMeeting,
            LocalDateTime now) {

        Objects.requireNonNull(developerId, "developerId must not be null");
        Objects.requireNonNull(urgentMeeting, "urgentMeeting must not be null");
        Objects.requireNonNull(now, "now must not be null");

        log.info("Interruption received: developer={}, meeting='{}', startsAt={}, window={}min",
                developerId, urgentMeeting.getTitle(), urgentMeeting.getStartTime(),
                ChronoUnit.MINUTES.between(now, urgentMeeting.getStartTime()));

        // Query actual Deep Work accumulated today
        int accumulated = timeBlockRepository.sumDeepWorkMinutesByDeveloperAndDate(
                developerId, now.toLocalDate());

        // Reconstruct the developer's current cognitive state
        // In a production system this is stored in Redis; for MVP we derive it from accumulated data
        DeveloperState currentState = reconstructCurrentState(developerId, now, accumulated);

        // DESIGN PATTERN: State Pattern — the handler delegates to the state
        InterruptionResult result = interruptionHandler.handle(currentState, urgentMeeting, now, accumulated);

        log.info("Interruption evaluated: scenario={}, window={}min, blocks={}",
                result.getScenario(), result.getWindowMinutes(), result.getProposedBlocks().size());

        // Notify the developer immediately via WebSocket
        notificationPort.notifyInterruption(developerId, result);

        // Auto-apply results that do not require a developer decision
        if (result.getScenario() != InterruptionScenario.DECISION_REQUIRED) {
            applyResultBlocks(developerId, result.getProposedBlocks(), now.toLocalDate());
            notificationPort.notifyStateChange(
                    developerId,
                    resolveNotificationState(result),
                    result.getMessage());
        }

        return result;
    }

    @Override
    public ScheduleDay applyInterruptionDecision(
            String developerId,
            InterruptionDecision decision,
            String meetingId) {

        Objects.requireNonNull(developerId, "developerId must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(meetingId, "meetingId must not be null");

        LocalDateTime now = LocalDateTime.now();

        ScheduleDay day = scheduleDayRepository
                .findByDeveloperIdAndDate(developerId, now.toLocalDate())
                .orElseThrow(() -> new IllegalStateException(
                        "No schedule found for developer=" + developerId + " on " + now.toLocalDate()));

        Meeting targetMeeting = day.getMeetings().stream()
                .filter(m -> m.getId().toString().equals(meetingId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

        List<TimeBlock> existingBlocks = new ArrayList<>(day.getBlocks());
        List<TimeBlock> decisionBlocks;

        if (decision == InterruptionDecision.SWITCH_TO_SHALLOW) {
            decisionBlocks = buildOptionABlocks(now, targetMeeting);
            notificationPort.notifyStateChange(developerId, CognitiveState.SHALLOW_WORK,
                    "Opção A aplicada: Mudando para Shallow Work até a reunião.");
        } else {
            decisionBlocks = buildOptionBBlocks(now, targetMeeting);
            notificationPort.notifyStateChange(developerId, CognitiveState.DEEP_WORK,
                    String.format("Opção B aplicada: Continuando Deep Work. " +
                                  "Descanso obrigatório às %s.",
                                  targetMeeting.getStartTime()
                                              .minusMinutes(EngineConstants.PRE_MEETING_REST)
                                              .toLocalTime()));
        }

        existingBlocks.addAll(decisionBlocks);
        ScheduleDay updated = day.withBlocks(existingBlocks);
        ScheduleDay saved = scheduleDayRepository.save(updated);

        notificationPort.notifyScheduleUpdate(developerId, saved);
        return saved;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Option A: Switch to SHALLOW_WORK immediately.
     * Shallow Work from now until meeting start; meeting itself is also SHALLOW_WORK.
     */
    private List<TimeBlock> buildOptionABlocks(LocalDateTime now, Meeting meeting) {
        long windowMinutes = ChronoUnit.MINUTES.between(now, meeting.getStartTime());
        List<TimeBlock> blocks = new ArrayList<>();
        if (windowMinutes > 0) {
            blocks.add(TimeBlock.create(now, (int) windowMinutes, CognitiveState.SHALLOW_WORK));
        }
        blocks.add(TimeBlock.create(
                meeting.getStartTime(), meeting.getDurationMinutes(), CognitiveState.SHALLOW_WORK));
        return blocks;
    }

    /**
     * Option B: Continue DEEP_WORK; mandatory FORCED_REST at M_start − 20 min.
     *
     * Timeline:
     * now → (M_start − 20 min): DEEP_WORK (counted toward L_max)
     * (M_start − 20 min) → M_start: FORCED_REST (exactly 20 min)
     * M_start → M_end: SHALLOW_WORK (the meeting)
     */
    private List<TimeBlock> buildOptionBBlocks(LocalDateTime now, Meeting meeting) {
        LocalDateTime mandatoryRestStart = meeting.getStartTime().minusMinutes(EngineConstants.PRE_MEETING_REST);
        long deepWorkMinutes = ChronoUnit.MINUTES.between(now, mandatoryRestStart);

        List<TimeBlock> blocks = new ArrayList<>();

        if (deepWorkMinutes > 0) {
            blocks.add(TimeBlock.create(now, (int) deepWorkMinutes, CognitiveState.DEEP_WORK));
        }

        // Mandatory 20-min pre-meeting rest
        blocks.add(TimeBlock.create(mandatoryRestStart, EngineConstants.PRE_MEETING_REST, CognitiveState.FORCED_REST));

        // Meeting
        blocks.add(TimeBlock.create(
                meeting.getStartTime(), meeting.getDurationMinutes(), CognitiveState.SHALLOW_WORK));

        return blocks;
    }

    /**
     * Reconstructs the developer's current cognitive state from persisted data.
     *
     * MVP simplification: derives state from accumulated Deep Work.
     * Production systems would store the current state in Redis or a session store.
     */
    private DeveloperState reconstructCurrentState(
            String developerId, LocalDateTime now, int accumulated) {

        if (accumulated >= EngineConstants.L_MAX) {
            log.debug("developer={} has reached L_max — state=SHALLOW_WORK", developerId);
            return new ShallowWorkState();
        }

        // Heuristic: assume Deep Work started 30 min ago (MVP approximation)
        // Production: load actual block start time from persisted session state
        LocalDateTime presumedBlockStart = now.minusMinutes(30);
        log.debug("developer={} state=DEEP_WORK (reconstructed, start={})", developerId, presumedBlockStart);
        return new DeepWorkState(presumedBlockStart, accumulated);
    }

    private void applyResultBlocks(
            String developerId, List<TimeBlock> blocks, LocalDate date) {
        ScheduleDay day = scheduleDayRepository
                .findByDeveloperIdAndDate(developerId, date)
                .orElseGet(() -> ScheduleDay.createEmpty(developerId, date));

        List<TimeBlock> merged = new ArrayList<>(day.getBlocks());
        merged.addAll(blocks);
        scheduleDayRepository.save(day.withBlocks(merged));
    }

    private CognitiveState resolveNotificationState(InterruptionResult result) {
        return switch (result.getScenario()) {
            case EMERGENCY_COOLDOWN -> CognitiveState.FORCED_REST;
            case SAFE_AUTO_TRUNCATION -> CognitiveState.DEEP_WORK; // engine auto-handles transition
            default -> CognitiveState.IDLE;
        };
    }
}
