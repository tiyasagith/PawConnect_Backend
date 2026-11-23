package com.example.PawConnect.service.impl;

import com.example.PawConnect.dto.ServiceProviderAvailabilityDto;
import com.example.PawConnect.dto.request.professional.UpdateAvailabilityRequest;
import com.example.PawConnect.dto.response.ProviderWeeklyAvailabilityResponse;
import com.example.PawConnect.exception.BusinessRuleException;
import com.example.PawConnect.exception.ForbiddenException;
import com.example.PawConnect.model.ServiceProvider;
import com.example.PawConnect.model.ServiceProviderAvailability;
import com.example.PawConnect.repository.ServiceProviderAvailabilityRepository;
import com.example.PawConnect.repository.ServiceProviderRepository;
import com.example.PawConnect.service.CurrentUserService;
import com.example.PawConnect.service.ServiceProviderAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServiceProviderAvailabilityServiceImpl implements ServiceProviderAvailabilityService {

    private final CurrentUserService currentUserService;
    private final ServiceProviderRepository serviceProviderRepository;
    private final ServiceProviderAvailabilityRepository availabilityRepository;

    @Override
    @Transactional(readOnly = true)
    public ProviderWeeklyAvailabilityResponse getCurrentProviderAvailability() {
        ServiceProvider provider = getAuthenticatedProvider();
        boolean hasConfiguredAvailability = availabilityRepository.existsByProviderIdAndIsConfiguredTrue(provider.getId());

        if (!hasConfiguredAvailability) {
            return ProviderWeeklyAvailabilityResponse.builder()
                    .configured(false)
                    .message("Provider availability has not been configured")
                    .providerId(provider.getId())
                    .days(List.of())
                    .build();
        }

        return buildConfiguredResponse(provider.getId(), "Provider availability retrieved successfully");
    }

    @Override
    @Transactional
    public ProviderWeeklyAvailabilityResponse updateCurrentProviderAvailability(
            List<UpdateAvailabilityRequest> schedule
    ) {
        ServiceProvider provider = getAuthenticatedProvider();
        if (schedule == null || schedule.isEmpty()) {
            throw new BusinessRuleException("Schedule cannot be empty");
        }

        Map<DayOfWeek, UpdateAvailabilityRequest> requestedDays = new EnumMap<>(DayOfWeek.class);
        for (UpdateAvailabilityRequest day : schedule) {
            if (day.getDayOfWeek() == null) {
                throw new BusinessRuleException("Day of week is required");
            }
            if (requestedDays.put(day.getDayOfWeek(), day) != null) {
                throw new BusinessRuleException("A day of the week may only appear once in the schedule");
            }
            validateDaySchedule(day);
        }

        for (UpdateAvailabilityRequest day : requestedDays.values()) {
            saveDaySchedule(provider, day);
        }

        return buildConfiguredResponse(provider.getId(), "Provider availability updated successfully");
    }

    @Override
    @Transactional
    public ProviderWeeklyAvailabilityResponse updateCurrentProviderDayAvailability(
            DayOfWeek dayOfWeek,
            UpdateAvailabilityRequest dayRequest
    ) {
        ServiceProvider provider = getAuthenticatedProvider();
        if (dayOfWeek == null) {
            throw new BusinessRuleException("Day of week is required");
        }
        if (dayRequest == null) {
            throw new BusinessRuleException("Availability payload is required");
        }
        dayRequest.setDayOfWeek(dayOfWeek);
        validateDaySchedule(dayRequest);

        saveDaySchedule(provider, dayRequest);

        return buildConfiguredResponse(provider.getId(), "Provider availability updated successfully");
    }

    private void validateDaySchedule(UpdateAvailabilityRequest day) {
        if (Boolean.TRUE.equals(day.getIsAvailable())) {
            if (day.getStartTime() == null || day.getEndTime() == null) {
                throw new BusinessRuleException("Start time and end time are required for " + day.getDayOfWeek());
            }
            if (!day.getEndTime().isAfter(day.getStartTime())) {
                throw new BusinessRuleException("Start time must be before end time for " + day.getDayOfWeek());
            }
            if (day.getSlotDurationMinutes() == null) {
                throw new BusinessRuleException("Slot duration is required for " + day.getDayOfWeek());
            }
            int slotDuration = day.getSlotDurationMinutes();
            if (slotDuration < 10 || slotDuration > 180) {
                throw new BusinessRuleException("Slot duration must be between 10 and 180 minutes for " + day.getDayOfWeek());
            }
        } else {
            // When day is marked unavailable, validate time window only if both are supplied
            if (day.getStartTime() != null && day.getEndTime() != null && !day.getEndTime().isAfter(day.getStartTime())) {
                throw new BusinessRuleException("Start time must be before end time for " + day.getDayOfWeek());
            }
            if (day.getSlotDurationMinutes() != null) {
                int slotDuration = day.getSlotDurationMinutes();
                if (slotDuration < 10 || slotDuration > 180) {
                    throw new BusinessRuleException("Slot duration must be between 10 and 180 minutes for " + day.getDayOfWeek());
                }
            }
        }
    }

    private void saveDaySchedule(ServiceProvider provider, UpdateAvailabilityRequest day) {
        ServiceProviderAvailability availability = availabilityRepository
                .findByProviderIdAndDayOfWeek(provider.getId(), day.getDayOfWeek())
                .orElseGet(() -> ServiceProviderAvailability.builder()
                        .provider(provider)
                        .dayOfWeek(day.getDayOfWeek())
                        .build());

        availability.setStartTime(day.getStartTime());
        availability.setEndTime(day.getEndTime());
        availability.setSlotDurationMinutes(day.getSlotDurationMinutes());
        availability.setIsAvailable(Boolean.TRUE.equals(day.getIsAvailable()));
        availability.setIsConfigured(true);

        availabilityRepository.save(availability);
    }

    private ProviderWeeklyAvailabilityResponse buildConfiguredResponse(Long providerId, String message) {
        Map<DayOfWeek, ServiceProviderAvailability> stored = new EnumMap<>(DayOfWeek.class);
        availabilityRepository.findByProviderId(providerId)
                .forEach(day -> stored.put(day.getDayOfWeek(), day));

        List<ServiceProviderAvailabilityDto> days = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            ServiceProviderAvailability record = stored.get(day);
            if (record != null) {
                days.add(ServiceProviderAvailabilityDto.builder()
                        .id(record.getId())
                        .providerId(providerId)
                        .dayOfWeek(day)
                        .startTime(record.getStartTime())
                        .endTime(record.getEndTime())
                        .slotDurationMinutes(record.getSlotDurationMinutes())
                        .isAvailable(Boolean.TRUE.equals(record.getIsAvailable()))
                        .isConfigured(Boolean.TRUE.equals(record.getIsConfigured()))
                        .build());
            } else {
                days.add(ServiceProviderAvailabilityDto.builder()
                        .id(null)
                        .providerId(providerId)
                        .dayOfWeek(day)
                        .startTime(null)
                        .endTime(null)
                        .slotDurationMinutes(null)
                        .isAvailable(false)
                        .isConfigured(false)
                        .build());
            }
        }

        days.sort(Comparator.comparingInt(d -> d.getDayOfWeek().getValue()));

        return ProviderWeeklyAvailabilityResponse.builder()
                .configured(true)
                .message(message)
                .providerId(providerId)
                .days(days)
                .build();
    }

    private ServiceProvider getAuthenticatedProvider() {
        Long userId = currentUserService.getCurrentUserId();
        return serviceProviderRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Authenticated user is not registered as a service provider"));
    }
}
