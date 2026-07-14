package com.cognitivemanager.domain.model;

/**
 * Enumeration of all possible cognitive states a developer can be in.
 *
 * Used by the State Pattern ({@link com.cognitivemanager.domain.state.DeveloperState})
 * and as the label for every {@link TimeBlock}.
 *
 * States:
 * <ul>
 *   <li>DEEP_WORK     — maximum cognitive focus; must be protected from interruptions.</li>
 *   <li>SHALLOW_WORK  — low-cognitive-load tasks: emails, meetings, admin.</li>
 *   <li>FORCED_REST   — mandatory recovery window; cannot be skipped or shortened
 *                       except by an overriding meeting (Cenário 1 emergency).</li>
 *   <li>IDLE          — no active block; engine is ready to allocate the next window.</li>
 * </ul>
 */
public enum CognitiveState {
    DEEP_WORK,
    SHALLOW_WORK,
    FORCED_REST,
    IDLE
}
