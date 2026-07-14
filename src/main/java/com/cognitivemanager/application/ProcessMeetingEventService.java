package com.cognitivemanager.application;

import com.cognitivemanager.domain.model.CognitiveState;
import com.cognitivemanager.domain.model.Meeting;
import com.cognitivemanager.domain.model.ScheduleDay;
import com.cognitivemanager.port.in.AllocateTimeUseCase;
import com.cognitivemanager.port.in.HandleInterruptionUseCase;
import com.cognitivemanager.port.in.ProcessMeetingEventUseCase;
import com.cognitivemanager.port.output.NotificationPort;
import com.cognitivemanager.port.output.ScheduleDayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service implementing {@link ProcessMeetingEventUseCase}.
 *
 * This is the entry point of the event-driven pipeline (RF01).
 * Receives translated domain {@link Meeting} objects from the RabbitMQ adapter,
 * determines the appropriate action (standard re-allocation vs. urgent interruption),
 * and delegates to the relevant use case.
 */
@Service
@Transactional
public class ProcessMeetingEventService implements ProcessMeetingEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessMeetingEventService.class);

    /** Default workday boundaries — configurable via {@code cognitive.engine.*} in production */
    private static final LocalTime WORKDAY_START = LocalTime.of(9, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(18, 0);

    private final AllocateTimeUseCase allocateTimeUseCase;
    private final HandleInterruptionUseCase handleInterruptionUseCase;
    private final ScheduleDayRepository scheduleDayRepository;
    private final NotificationPort notificationPort;

    public ProcessMeetingEventService(
            AllocateTimeUseCase allocateTimeUseCase,
            HandleInterruptionUseCase handleInterruptionUseCase,
            ScheduleDayRepository scheduleDayRepository,
            NotificationPort notificationPort) {
        this.allocateTimeUseCase = Objects.requireNonNull(allocateTimeUseCase);
        this.handleInterruptionUseCase = Objects.requireNonNull(handleInterruptionUseCase);
        this.scheduleDayRepository = Objects.requireNonNull(scheduleDayRepository);
        this.notificationPort = Objects.requireNonNull(notificationPort);
    }

    @Override
    public void processMeetingEvent(Meeting meeting, String developerId) {
        Objects.requireNonNull(meeting, "meeting must not be null");
        Objects.requireNonNull(developerId, "developerId must not be null");

        log.info("Processing meeting event: developer={}, meeting='{}', start={}, urgent={}",
                developerId, meeting.getTitle(), meeting.getStartTime(), meeting.isUrgent());

        LocalDate meetingDate = meeting.getStartTime().toLocalDate();

        // 1. Persist the new meeting into the day's aggregate
        ScheduleDay day = scheduleDayRepository
                .findByDeveloperIdAndDate(developerId, meetingDate)
                .orElseGet(() -> ScheduleDay.createEmpty(developerId, meetingDate));

        scheduleDayRepository.save(day.withMeeting(meeting));

        // 2. Determine action based on urgency and timing
        boolean isToday = meetingDate.isEqual(LocalDate.now());
        LocalDateTime now = LocalDateTime.now();

        if (isToday && meeting.isUrgent() && meeting.getStartTime().isAfter(now)) {
            // Urgent meeting for today → triggers full interruption evaluation (RF04, RF05)
            log.info("Urgent interruption path: developer={}, meeting='{}'", developerId, meeting.getTitle());
            handleInterruptionUseCase.handleInterruption(developerId, meeting, now);
            // The interruption handler triggers its own re-allocation and notifications
            return;
        }

        // 3. Standard path: re-allocate the affected day
        LocalDateTime workdayStart = meetingDate.atTime(WORKDAY_START);
        LocalDateTime workdayEnd = meetingDate.atTime(WORKDAY_END);

        allocateTimeUseCase.reallocateDay(developerId, meetingDate, workdayStart, workdayEnd);

        log.info("Standard re-allocation complete: developer={}, date={}", developerId, meetingDate);
    }

    @Override
    public void processMeetingCancellation(String meetingId, LocalDate date, String developerId) {
        Objects.requireNonNull(meetingId, "meetingId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(developerId, "developerId must not be null");

        log.info("Processing meeting cancellation: developer={}, meetingId={}, date={}",
                developerId, meetingId, date);

        scheduleDayRepository.findByDeveloperIdAndDate(developerId, date).ifPresent(day -> {
            // Remove the cancelled meeting from the aggregate
            ScheduleDay updated = day.withoutMeeting(UUID.fromString(meetingId));
            scheduleDayRepository.save(updated);

            // Re-allocate to reclaim the freed time window
            LocalDateTime workdayStart = date.atTime(WORKDAY_START);
            LocalDateTime workdayEnd = date.atTime(WORKDAY_END);
            allocateTimeUseCase.reallocateDay(developerId, date, workdayStart, workdayEnd);

            notificationPort.notifyStateChange(
                    developerId,
                    CognitiveState.IDLE,
                    String.format("Reunião cancelada. Janela de tempo recuperada em %s. " +
                                  "Agenda recalculada.", date));

            log.info("Cancellation processed: developer={}, meetingId={}, date={}", developerId, meetingId, date);
        });
    }
}
