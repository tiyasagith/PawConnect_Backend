package com.example.PawConnect.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VeterinarianSlotsResponse {
    private Long veterinarianId;
    private LocalDate date;
    private boolean isConfigured;
    private Integer slotDurationMinutes;
    private List<VeterinarianTimeSlotResponse> slots;
}
