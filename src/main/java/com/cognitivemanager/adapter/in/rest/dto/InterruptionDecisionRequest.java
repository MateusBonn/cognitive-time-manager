package com.cognitivemanager.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST request body for the developer's decision in a DECISION_REQUIRED interruption.
 *
 * {@code switchToShallow = true}  → SWITCH_TO_SHALLOW (Option A)
 * {@code switchToShallow = false} → PERSIST_DEEP_WORK  (Option B)
 */
public record InterruptionDecisionRequest(
        @JsonProperty("meeting_id") String meetingId,
        @JsonProperty("switch_to_shallow") boolean switchToShallow
) {}
