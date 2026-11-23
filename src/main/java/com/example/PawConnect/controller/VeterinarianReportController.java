package com.example.PawConnect.controller;

import com.example.PawConnect.dto.response.PractitionerPerformanceReportDTO;
import com.example.PawConnect.response.ApiResponse;
import com.example.PawConnect.service.PractitionerReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/vet/reports")
@RequiredArgsConstructor
@Tag(name = "Veterinarian Performance Reports", description = "Patient tracking and clinical practice analytics for authenticated veterinarians")
@PreAuthorize("hasRole('VETERINARIAN')")
public class VeterinarianReportController {

    private final PractitionerReportService practitionerReportService;

    @GetMapping("/patients")
    @Operation(summary = "Retrieve clinical patient tracking and performance report for the authenticated veterinarian")
    public ResponseEntity<ApiResponse<PractitionerPerformanceReportDTO>> getPatientReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status
    ) {
        PractitionerPerformanceReportDTO report = practitionerReportService.getVeterinarianPatientReport(
                startDate, endDate, status
        );
        return ResponseEntity.ok(ApiResponse.ok(report, "Veterinarian patient report retrieved successfully"));
    }
}

