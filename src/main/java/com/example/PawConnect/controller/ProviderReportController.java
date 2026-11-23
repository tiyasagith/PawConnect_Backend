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
@RequestMapping("/api/provider/reports")
@RequiredArgsConstructor
@Tag(name = "Service Provider Performance Reports", description = "Client session tracking and booking activity analytics for authenticated service providers")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderReportController {

    private final PractitionerReportService practitionerReportService;

    @GetMapping("/clients")
    @Operation(summary = "Retrieve client tracking and performance report for the authenticated service provider")
    public ResponseEntity<ApiResponse<PractitionerPerformanceReportDTO>> getClientReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status
    ) {
        PractitionerPerformanceReportDTO report = practitionerReportService.getServiceProviderClientReport(
                startDate, endDate, status
        );
        return ResponseEntity.ok(ApiResponse.ok(report, "Service provider client report retrieved successfully"));
    }
}

