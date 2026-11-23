package com.example.PawConnect.service;

import com.example.PawConnect.dto.VeterinarianAvailabilityDto;
import com.example.PawConnect.dto.request.professional.UpdateAvailabilityRequest;
import com.example.PawConnect.dto.response.VeterinarianSlotsResponse;

import java.time.LocalDate;
import java.util.List;

public interface VeterinarianAvailabilityService {
    List<VeterinarianAvailabilityDto> getCurrentVeterinarianAvailability();

    List<VeterinarianAvailabilityDto> updateCurrentVeterinarianAvailability(
            List<UpdateAvailabilityRequest> schedule
    );

    VeterinarianSlotsResponse getAvailableSlots(Long veterinarianId, LocalDate date);
}
