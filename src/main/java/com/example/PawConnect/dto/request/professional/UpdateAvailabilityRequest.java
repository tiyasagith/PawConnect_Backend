package com.example.PawConnect.dto.request.professional;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAvailabilityRequest {

    @NotNull(message = "Day of week is required")
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer slotDurationMinutes;

    private Boolean isAvailable;

    @AssertTrue(message = "Start time is required when day is available")
    public boolean isStartTimeValid() {
        if (Boolean.TRUE.equals(isAvailable)) {
            return startTime != null;
        }
        return true;
    }

    @AssertTrue(message = "End time is required when day is available")
    public boolean isEndTimeValid() {
        if (Boolean.TRUE.equals(isAvailable)) {
            return endTime != null;
        }
        return true;
    }

    @AssertTrue(message = "End time must be after start time")
    public boolean isTimeWindowValid() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return endTime.isAfter(startTime);
    }

    @AssertTrue(message = "Slot duration must be between 10 and 180 minutes when day is available")
    public boolean isSlotDurationValid() {
        if (Boolean.TRUE.equals(isAvailable)) {
            return slotDurationMinutes != null && slotDurationMinutes >= 10 && slotDurationMinutes <= 180;
        }
        if (slotDurationMinutes != null) {
            return slotDurationMinutes >= 10 && slotDurationMinutes <= 180;
        }
        return true;
    }
}
