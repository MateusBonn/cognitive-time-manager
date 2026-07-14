package com.cognitivemanager.port.output;

import com.cognitivemanager.domain.model.CognitiveState;
import com.cognitivemanager.domain.model.InterruptionResult;
import com.cognitivemanager.domain.model.ScheduleDay;

/**
 * SECONDARY PORT (Driven / Outbound)
 *
 * Contract for real-time developer notifications.
 * The domain signals events through this port; the concrete adapter
 * ({@link com.cognitivemanager.adapter.out.notification.WebSocketNotificationAdapter})
 * decides the transport mechanism (WebSocket, SSE, push notification, etc.).
 *
 * Decoupling notification transport from domain logic satisfies RNF02 (minimal coupling)
 * and makes it trivial to switch from WebSocket to SSE or add Slack/email channels
 * without touching any domain code.
 */
public interface NotificationPort {

    /**
     * Notifies the developer that their cognitive state has changed.
     * Called after every state transition (Deep Work → Rest, Rest → Idle, etc.).
     *
     * @param developerId Recipient developer
     * @param newState    The new cognitive state
     * @param message     Human-readable explanation of the transition
     */
    void notifyStateChange(String developerId, CognitiveState newState, String message);

    /**
     * Notifies the developer of an interruption result.
     * For DECISION_REQUIRED results, the notification includes both options
     * so the frontend can render an interactive choice dialog.
     *
     * @param developerId Recipient developer
     * @param result      The full interruption result (scenario, blocks, message)
     */
    void notifyInterruption(String developerId, InterruptionResult result);

    /**
     * Notifies the developer that their schedule has been recalculated.
     * Sent after every {@link com.cognitivemanager.port.in.AllocateTimeUseCase} call
     * so the dashboard re-renders with the latest block layout.
     *
     * @param developerId Recipient developer
     * @param updatedDay  The new, fully allocated schedule
     */
    void notifyScheduleUpdate(String developerId, ScheduleDay updatedDay);
}
