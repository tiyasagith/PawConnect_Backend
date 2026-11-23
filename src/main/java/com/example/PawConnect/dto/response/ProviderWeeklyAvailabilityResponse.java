package com.example.PawConnect.dto.response;

import com.example.PawConnect.dto.ServiceProviderAvailabilityDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderWeeklyAvailabilityResponse {
    private boolean configured;
    private String message;
    private Long providerId;
    @Builder.Default
    private List<ServiceProviderAvailabilityDto> days = new ArrayList<>();
}
