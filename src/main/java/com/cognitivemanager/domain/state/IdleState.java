package com.cognitivemanager.domain.state;

import com.cognitivemanager.domain.model.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * DESIGN PATTERN: State Pattern — Concrete State: IDLE
 *
 * No active cognitive block. The developer is between allocated blocks,
 * either because the day just started, a rest period just completed,
 * or the last block finished and the engine hasn't started the next one yet.
 *
 * This is the entry state when the system initialises for the day.
 * The engine transitions here after every {@link ForcedRestState} completion.
 *
 * Transitions:
 * <ul>
 *   <li>Engine triggers allocation → {@link DeepWorkState} or {@link ShallowWorkState}</li>
 *   <li>Meeting interruption → schedules meeting as SHALLOW_WORK</li>
 * </ul>
 */
public final class IdleState implements DeveloperState {

    @Override
    public CognitiveState getCognitiveState() {
        return CognitiveState.IDLE;
    }

    /**
     * In IDLE state, an incoming meeting is simply added as a SHALLOW_WORK block.
     * No interruption scenario evaluation is needed because there is no active block.
     */
    @Override
    public InterruptionResult handleMeetingInterruption(
            Meeting urgentMeeting, LocalDateTime now, int accumulatedDeepWork) {

        long windowMinutes = ChronoUnit.MINUTES.between(now, urgentMeeting.getStartTime());

        List<TimeBlock> blocks = List.of(
                TimeBlock.create(
                        urgentMeeting.getStartTime(),
                        urgentMeeting.getDurationMinutes(),
                        CognitiveState.SHALLOW_WORK));

        return InterruptionResult.safeAutoTruncation(urgentMeeting, blocks, windowMinutes);
    }

    /** IDLE is a stable resting state; the engine drives the next transition. */
    @Override
    public DeveloperState transition(LocalDateTime now) {
        return this;
    }

    @Override
    public String getStateDescription() {
        return "Idle: Sem bloco ativo. Pronto para próxima alocação cognitiva.";
    }

    /** IDLE is the canonical state for starting a new block. */
    @Override
    public boolean canStartNewBlock() {
        return true;
    }
}
