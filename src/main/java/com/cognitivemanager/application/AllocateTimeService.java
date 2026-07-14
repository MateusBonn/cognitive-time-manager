package com.cognitivemanager.application;

import com.cognitivemanager.domain.engine.TimeAllocationEngine;
import com.cognitivemanager.domain.model.ScheduleDay;
import com.cognitivemanager.port.in.AllocateTimeUseCase;
import com.cognitivemanager.port.out.NotificationPort;
import com.cognitivemanager.port.out.ScheduleDayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Application service implementing {@link AllocateTimeUseCase}.
 *
 * Orchestrates the domain objects and infrastructure adapters.
 * Contains no business logic — that lives entirely in {@link TimeAllocationEngine}
 * and the Strategy classes. This service only coordinates the flow.
 *
 * {@code @Service} and {@code @Transactional} are acceptable here because this
 * class is in the APPLICATION layer, not the DOMAIN layer (RNF03 applies only
 * to domain classes under {@code com.cognitivemanager.domain.*}).
 */
@Service
@Transactional
public class AllocateTimeService implements AllocateTimeUseCase {

    private static final Logger log = LoggerFactory.getLogger(AllocateTimeService.class);

    private final TimeAllocationEngine engine;
    private final ScheduleDayRepository scheduleDayRepository;
    private final NotificationPort notificationPort;

    public AllocateTimeService(
            ScheduleDayRepository scheduleDayRepository,
            NotificationPort notificationPort) {
        this.engine = new TimeAllocationEngine() // Pure domain object, no injection needed
        this.scheduleDayRepository = Objects.requireNonNull(scheduleDayRepository);
        this.notificationPort = Objects.requireNonNull(notificationPort);
    }

    @Override
    public ScheduleDay allocateDay(
            String developerId,
            LocalDate date,
            LocalDateTime workdayStart,
            LocalDateTime workdayEnd) {

        log.debug("Allocating day for developer={}, date={}", developerId, date);
        long startNano = System.nanoTime();

        // Load existing schedule or create an empty one
        ScheduleDay day = scheduleDayRepository
                .findByDeveloperIdAndDate(developerId, date)
                .orElseGet(() -> ScheduleDay.createEmpty(developerId, date));

        // Run the engine — all math happens here, pure domain computation
        ScheduleDay allocated = engine.processDay(day, workdayStart, workdayEnd);

        // Persist the result
        ScheduleDay saved = scheduleDayRepository.save(allocated);

        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
        log.info("Day allocated: developer={}, date={}, blocks={}, deepWork={}min, elapsed={}ms",
                developerId, date, saved.getBlocks().size(),
                saved.getCumulativeDeepWorkMinutes(), elapsedMs);

        // Notify the developer via WebSocket (non-blocking — adapter handles transport)
        notificationPort.notifyScheduleUpdate(developerId, saved);

        return saved;
    }

    @Override
    public ScheduleDay reallocateDay(
            String developerId,
            LocalDate date,
            LocalDateTime workdayStart,
            LocalDateTime workdayEnd) {

        log.debug("Re-allocating day for developer={}, date={}", developerId, date);
        // Same logic as allocateDay. The day.withBlocks() call in engine.processDay()
        // replaces all previous blocks, effectively clearing the stale schedule.
        return allocateDay(developerId, date, workdayStart, workdayEnd);
    }
}
