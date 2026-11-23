package com.example.PawConnect.controller;

import com.example.PawConnect.dto.request.professional.UpdateAvailabilityRequest;
import com.example.PawConnect.dto.request.professional.UpdateWeeklyAvailabilityRequest;
import com.example.PawConnect.dto.response.ProviderWeeklyAvailabilityResponse;
import com.example.PawConnect.response.ApiResponse;
import com.example.PawConnect.service.ServiceProviderAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;

@RestController
@RequestMapping("/api/provider/availability")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderAvailabilityController {

    private final ServiceProviderAvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProviderWeeklyAvailabilityResponse>> getAvailability() {
        ProviderWeeklyAvailabilityResponse response = availabilityService.getCurrentProviderAvailability();
        return ResponseEntity.ok(ApiResponse.ok(
                response,
                response.isConfigured() ? "Provider availability retrieved successfully" : "Provider availability has not been configured"
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProviderWeeklyAvailabilityResponse>> updateAvailability(
            @Valid @RequestBody UpdateWeeklyAvailabilityRequest request
    ) {
        ProviderWeeklyAvailabilityResponse response = availabilityService.updateCurrentProviderAvailability(request.getSchedule());
        return ResponseEntity.ok(ApiResponse.ok(
                response,
                "Provider availability updated successfully"
        ));
    }

    @PutMapping("/{dayOfWeek}")
    public ResponseEntity<ApiResponse<ProviderWeeklyAvailabilityResponse>> updateDayAvailability(
            @PathVariable DayOfWeek dayOfWeek,
            @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        ProviderWeeklyAvailabilityResponse response = availabilityService.updateCurrentProviderDayAvailability(dayOfWeek, request);
        return ResponseEntity.ok(ApiResponse.ok(
                response,
                "Provider availability updated successfully"
        ));
    }
}
