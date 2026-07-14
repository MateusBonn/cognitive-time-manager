package com.cognitivemanager.adapter.in.rest.dto;

import com.cognitivemanager.domain.model.ScheduleDay;
import com.cognitivemanager.domain.model.TimeBlock;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for a developer's full schedule day.
 * Maps from the domain {@link ScheduleDay} aggregate, exposing only the
 * fields meaningful to the frontend — keeping persistence IDs and internal
 * accumulation counters visible for debugging.
 */
public record ScheduleDayResponse(

        @JsonProperty("id")
        UUID id,

        @JsonProperty("developer_id")
        String developerId,

        @JsonProperty("date")
        LocalDate date,

        @JsonProperty("cumulative_deep_work_minutes")
        int cumulativeDeepWorkMinutes,

        @JsonProperty("remaining_deep_work_capacity_minutes")
        int remainingDeepWorkCapacity,

        @JsonProperty("daily_limit_reached")
        boolean dailyLimitReached,

        @JsonProperty("blocks")
        List<TimeBlockResponse> blocks

) {

    public static ScheduleDayResponse from(ScheduleDay day) {
        return new ScheduleDayResponse(
                day.getId(),
                day.getDeveloperId(),
                day.getDate(),
                day.getCumulativeDeepWorkMinutes(),
                day.getRemainingDeepWorkCapacity(),
                day.isDailyDeepWorkLimitReached(),
                day.getBlocks().stream().map(TimeBlockResponse::from).toList()
        );
    }

    /**
     * DTO for an individual time block inside the schedule response.
     */
    public record TimeBlockResponse(

            @JsonProperty("id")
            UUID id,

            @JsonProperty("state")
            String state,

            @JsonProperty("start_time")
            String startTime,

            @JsonProperty("end_time")
            String endTime,

            @JsonProperty("duration_minutes")
            int durationMinutes,

            @JsonProperty("interrupted")
            boolean interrupted

    ) {
        public static TimeBlockResponse from(TimeBlock block) {
            return new TimeBlockResponse(
                    block.getId(),
                    block.getState().name(),
                    block.getStartTime().toString(),
                    block.getEndTime().toString(),
                    block.getDurationMinutes(),
                    block.isInterrupted()
            );
        }
    }
}
