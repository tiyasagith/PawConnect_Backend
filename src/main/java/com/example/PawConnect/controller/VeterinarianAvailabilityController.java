package com.example.PawConnect.controller;

import com.example.PawConnect.dto.VeterinarianAvailabilityDto;
import com.example.PawConnect.dto.request.professional.UpdateWeeklyAvailabilityRequest;
import com.example.PawConnect.response.ApiResponse;
import com.example.PawConnect.service.VeterinarianAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/vet/availability", "/api/veterinarians/availability", "/api/v1/veterinarians/availability"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('VETERINARIAN')")
public class VeterinarianAvailabilityController {

    private final VeterinarianAvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VeterinarianAvailabilityDto>>> getAvailability() {
        return ResponseEntity.ok(ApiResponse.ok(
                availabilityService.getCurrentVeterinarianAvailability(),
                "Availability retrieved successfully"
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<List<VeterinarianAvailabilityDto>>> updateAvailability(
            @Valid @RequestBody UpdateWeeklyAvailabilityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                availabilityService.updateCurrentVeterinarianAvailability(request.getSchedule()),
                "Availability updated successfully"
        ));
    }
}
