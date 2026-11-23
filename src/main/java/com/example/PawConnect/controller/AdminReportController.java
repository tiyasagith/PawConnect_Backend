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
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin Reports & Analytics", description = "Administrative performance reporting and practitioner activity tracking")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final PractitionerReportService practitionerReportService;

    @GetMapping("/practitioner-activity")
    @Operation(summary = "Retrieve aggregated performance, revenue, and patient log reports for all practitioners")
    public ResponseEntity<ApiResponse<List<PractitionerPerformanceReportDTO>>> getPractitionerActivity(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status
    ) {
        List<PractitionerPerformanceReportDTO> report = practitionerReportService.getAdminPractitionerActivity(
                type, practitionerId, startDate, endDate, status
        );
        return ResponseEntity.ok(ApiResponse.ok(report, "Practitioner activity reports retrieved successfully"));
    }
}

