package com.example.PawConnect.dto.request.professional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWeeklyAvailabilityRequest {

    @NotEmpty(message = "At least one availability day is required")
    private List<@Valid UpdateAvailabilityRequest> schedule;
}
