package com.example.PawConnect.service;

import com.example.PawConnect.dto.response.PractitionerPerformanceReportDTO;

import java.time.LocalDate;
import java.util.List;

public interface PractitionerReportService {

    List<PractitionerPerformanceReportDTO> getAdminPractitionerActivity(
            String type,
            Long practitionerId,
            LocalDate startDate,
            LocalDate endDate,
            String status
    );

    PractitionerPerformanceReportDTO getVeterinarianPatientReport(
            LocalDate startDate,
            LocalDate endDate,
            String status
    );

    PractitionerPerformanceReportDTO getServiceProviderClientReport(
            LocalDate startDate,
            LocalDate endDate,
            String status
    );
}

