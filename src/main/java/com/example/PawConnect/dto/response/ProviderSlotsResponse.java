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
public class ProviderSlotsResponse {
    private Long providerId;
    private Long serviceId;
    private LocalDate date;
    private Integer serviceDurationMinutes;
    private Integer slotDurationMinutes;
    private List<ProviderTimeSlotResponse> slots;
}
