package com.cognitivemanager.domain.strategy;

import com.cognitivemanager.domain.constants.EngineConstants;
import com.cognitivemanager.domain.model.CognitiveState;
import com.cognitivemanager.domain.model.TimeBlock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DESIGN PATTERN: Strategy Pattern — Concrete Strategy: Sparse Schedule
 *
 * Applied when the day has fewer than 3 meetings OR total meeting time < 120 min.
 * On a sparse day the developer has large uninterrupted windows, so the optimal
 * approach is to maximise rest (R_max = 25 min) between Deep Work blocks.
 * This prevents accumulated fatigue across multiple Deep Work cycles.
 *
 * Algorithm (recursive, per §4):
 * <pre>
 * allocate(windowStart, T_livre, accumulated):
 *   1. IF accumulated >= L_max:
 *        return [SHALLOW_WORK(windowStart, T_livre)]
 *   2. IF T_livre < D_min:
 *        return [SHALLOW_WORK(windowStart, T_livre)]
 *   3. D_alocado = min(T_livre, min(D_max, L_max - accumulated))
 *      criticalSpace = (T_livre - D_alocado) < (R_max + D_min)
 *      R_alocado = criticalSpace ? min(R_min, remainder) : min(R_max, remainder)
 *      T_novo = T_livre - D_alocado - R_alocado
 *      return [DEEP_WORK(windowStart, D_alocado),
 *              FORCED_REST(windowStart + D_alocado, R_alocado),
 *              ...allocate(windowStart + D_alocado + R_alocado, T_novo, accumulated + D_alocado)]
 * </pre>
 */
public final class SparseScheduleStrategy implements AllocationStrategy {

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

        // Base case 1: daily Deep Work limit reached — all remaining is SHALLOW
        if (accumulated >= EngineConstants.L_MAX) {
            if (freeMinutes > 0) {
                result.add(TimeBlock.create(windowStart, freeMinutes, CognitiveState.SHALLOW_WORK));
            }
            return;
        }

        // Base case 2: window too small for Deep Work (T_livre < D_min)
        if (freeMinutes < EngineConstants.D_MIN) {
            if (freeMinutes > 0) {
                result.add(TimeBlock.create(windowStart, freeMinutes, CognitiveState.SHALLOW_WORK));
            }
            return;
        }

        // Step 3: allocate Deep Work
        // D_alocado = min(T_livre, min(D_max, remaining capacity toward L_max))
        int remainingCapacity = EngineConstants.L_MAX - accumulated;
        int deepAllocated = Math.min(freeMinutes, Math.min(EngineConstants.D_MAX, remainingCapacity));

        result.add(TimeBlock.create(windowStart, deepAllocated, CognitiveState.DEEP_WORK));

        LocalDateTime afterDeep = windowStart.plusMinutes(deepAllocated);
        int remainingAfterDeep = freeMinutes - deepAllocated;

        if (remainingAfterDeep <= 0) {
            // No room for rest; window is exactly filled by Deep Work
            return;
        }

        // R_alocado decision:
        // criticalSpace = not enough room for rest + another full Deep Work block
        // Sparse strategy prefers R_max (full rest) unless space is critical
        boolean criticalSpace = remainingAfterDeep < (EngineConstants.R_MAX + EngineConstants.D_MIN);
        int restAllocated = criticalSpace
                ? Math.min(EngineConstants.R_MIN, remainingAfterDeep)
                : Math.min(EngineConstants.R_MAX, remainingAfterDeep);

        if (restAllocated > 0) {
            result.add(TimeBlock.create(afterDeep, restAllocated, CognitiveState.FORCED_REST));
        }

        // T_novo = T_livre - D_alocado - R_alocado
        int newFree = remainingAfterDeep - restAllocated;
        int newAccumulated = accumulated + deepAllocated;

        if (newFree > 0) {
            LocalDateTime newWindowStart = afterDeep.plusMinutes(restAllocated);
            // Recursive call — processes remaining window with updated accumulation
            allocateRecursive(newWindowStart, newFree, newAccumulated, result);
        }
    }

    @Override
    public String getStrategyName() {
        return "SPARSE_SCHEDULE";
    }

    /**
     * Sparse strategy applies when the day is light on meetings.
     * Threshold: < 3 meetings OR total meeting time < 120 min.
     */
    @Override
    public boolean isApplicable(int meetingCountToday, int totalMeetingMinutes) {
        return meetingCountToday < 3 || totalMeetingMinutes < 120;
    }
}
