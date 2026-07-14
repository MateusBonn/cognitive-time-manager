package com.cognitivemanager.domain.strategy;

import com.cognitivemanager.domain.constants.EngineConstants;
import com.cognitivemanager.domain.model.CognitiveState;
import com.cognitivemanager.domain.model.TimeBlock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DESIGN PATTERN: Strategy Pattern — Concrete Strategy: Dense Schedule
 *
 * Applied when the day has 3 or more meetings AND total meeting time ≥ 120 min.
 * On a dense day, free windows are fragmented and precious. The strategy favours
 * R_min (15 min) between Deep Work blocks to reclaim more usable focus time.
 *
 * Trade-off: shorter rest means the developer accumulates more fatigue per cycle,
 * but on a meeting-heavy day this is the pragmatic optimum — the meetings themselves
 * provide natural breaks between deep sessions.
 *
 * Algorithm is identical in structure to {@link SparseScheduleStrategy} but
 * always uses R_min for rest allocation regardless of remaining space.
 *
 * <pre>
 * allocate(windowStart, T_livre, accumulated):
 *   1. IF accumulated >= L_max: return [SHALLOW_WORK(T_livre)]
 *   2. IF T_livre < D_min: return [SHALLOW_WORK(T_livre)]
 *   3. D_alocado = min(T_livre, min(D_max, L_max - accumulated))
 *      R_alocado = min(R_min, remainder)   ← always R_min (dense strategy)
 *      T_novo = T_livre - D_alocado - R_alocado
 *      return [DEEP_WORK, FORCED_REST, ...allocate(T_novo, accumulated + D_alocado)]
 * </pre>
 */
public final class DenseScheduleStrategy implements AllocationStrategy {

    @Override
    public List<TimeBlock> allocate(LocalDateTime windowStart, int freeMinutes, int accumulatedDeepWork) {
        List<TimeBlock> result = new ArrayList<>();
        allocateRecursive(windowStart, freeMinutes, accumulatedDeepWork, result);
        return result;
    }

    private void allocateRecursive(
            LocalDateTime windowStart,
            int freeMinutes,
            int accumulated,
            List<TimeBlock> result) {

        // Base case 1: daily limit reached
        if (accumulated >= EngineConstants.L_MAX) {
            if (freeMinutes > 0) {
                result.add(TimeBlock.create(windowStart, freeMinutes, CognitiveState.SHALLOW_WORK));
            }
            return;
        }

        // Base case 2: insufficient window for Deep Work
        if (freeMinutes < EngineConstants.D_MIN) {
            if (freeMinutes > 0) {
                result.add(TimeBlock.create(windowStart, freeMinutes, CognitiveState.SHALLOW_WORK));
            }
            return;
        }

        // Step 3: allocate Deep Work
        // D_alocado = min(T_livre, min(D_max, remaining capacity))
        int remainingCapacity = EngineConstants.L_MAX - accumulated;
        int deepAllocated = Math.min(freeMinutes, Math.min(EngineConstants.D_MAX, remainingCapacity));

        result.add(TimeBlock.create(windowStart, deepAllocated, CognitiveState.DEEP_WORK));

        LocalDateTime afterDeep = windowStart.plusMinutes(deepAllocated);
        int remainingAfterDeep = freeMinutes - deepAllocated;

        if (remainingAfterDeep <= 0) {
            return;
        }

        // Dense strategy: ALWAYS use R_min — maximise available time in fragmented days
        int restAllocated = Math.min(EngineConstants.R_MIN, remainingAfterDeep);

        if (restAllocated > 0) {
            result.add(TimeBlock.create(afterDeep, restAllocated, CognitiveState.FORCED_REST));
        }

        int newFree = remainingAfterDeep - restAllocated;
        int newAccumulated = accumulated + deepAllocated;

        if (newFree > 0) {
            LocalDateTime newWindowStart = afterDeep.plusMinutes(restAllocated);
            allocateRecursive(newWindowStart, newFree, newAccumulated, result);
        }
    }

    @Override
    public String getStrategyName() {
        return "DENSE_SCHEDULE";
    }

    /**
     * Dense strategy applies when meeting load is high.
     * Both conditions must hold: ≥ 3 meetings AND ≥ 120 min of meetings.
     */
    @Override
    public boolean isApplicable(int meetingCountToday, int totalMeetingMinutes) {
        return meetingCountToday >= 3 && totalMeetingMinutes >= 120;
    }
}
