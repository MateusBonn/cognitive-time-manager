package com.cognitivemanager.domain.state;

import com.cognitivemanager.domain.model.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * DESIGN PATTERN: State Pattern — Concrete State: SHALLOW_WORK
 *
 * Represents low-cognitive-load tasks: answering emails, attending meetings,
 * writing documentation, code reviews, administrative work.
 *
 * Interruptions during Shallow Work carry no cognitive cost — the developer
 * is already in a low-focus mode. Meeting interruptions simply extend or
 * replace the current shallow period.
 *
 * Transitions:
 * <ul>
 *   <li>Shallow Work block completes → {@link IdleState}</li>
 *   <li>Meeting interruption → absorbed into Shallow (no scenario evaluation needed)</li>
 * </ul>
 */
public final class ShallowWorkState implements DeveloperState {

    @Override
    public CognitiveState getCognitiveState() {
        return CognitiveState.SHALLOW_WORK;
    }

    /**
     * During Shallow Work, meeting interruptions are low-impact.
     * The window to the meeting is simply kept as SHALLOW_WORK,
     * and the meeting itself is also SHALLOW_WORK.
     * No scenario evaluation (EMERGENCY / DECISION / SAFE) is needed.
     */
    @Override
    public InterruptionResult handleMeetingInterruption(
            Meeting urgentMeeting, LocalDateTime now, int accumulatedDeepWork) {

        long windowMinutes = ChronoUnit.MINUTES.between(now, urgentMeeting.getStartTime());

        List<TimeBlock> blocks = List.of(
                TimeBlock.create(now, (int) Math.max(1, windowMinutes), CognitiveState.SHALLOW_WORK),
                TimeBlock.create(urgentMeeting.getStartTime(),
                        urgentMeeting.getDurationMinutes(), CognitiveState.SHALLOW_WORK));

        return InterruptionResult.safeAutoTruncation(urgentMeeting, blocks, windowMinutes);
    }

    @Override
    public DeveloperState transition(LocalDateTime now) {
        // Transition to IDLE when block ends (driven externally by the engine)
        return this;
    }

    @Override
    public String getStateDescription() {
        return "Shallow Work: Tarefas de baixo esforço cognitivo. " +
               "Disponível para interrupções. Emails, reuniões, admin.";
    }

    @Override
    public boolean canStartNewBlock() {
        return true; // A new Deep Work block can begin after this if time allows
    }
}
