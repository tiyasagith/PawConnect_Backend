package com.example.PawConnect.service;

import com.example.PawConnect.dto.request.professional.UpdateAvailabilityRequest;
import com.example.PawConnect.dto.response.ProviderWeeklyAvailabilityResponse;

import java.time.DayOfWeek;
import java.util.List;

public interface ServiceProviderAvailabilityService {
    ProviderWeeklyAvailabilityResponse getCurrentProviderAvailability();

    ProviderWeeklyAvailabilityResponse updateCurrentProviderAvailability(
            List<UpdateAvailabilityRequest> schedule
    );

    ProviderWeeklyAvailabilityResponse updateCurrentProviderDayAvailability(
            DayOfWeek dayOfWeek,
            UpdateAvailabilityRequest dayRequest
    );
}
