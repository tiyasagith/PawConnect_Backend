package com.example.PawConnect.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PractitionerPerformanceReportDTO {
    private Long practitionerId;
    private Long userId;
    private String practitionerType; // "VETERINARIAN" or "SERVICE_PROVIDER"
    private String subType; // Specialization or ProviderType
    private String name;
    private String email;
    private String phone;
    private String clinicOrBusinessName;
    private int totalBookings;
    private int uniquePetsChecked;
    private int completedCount;
    private int confirmedCount;
    private int pendingCount;
    private int cancelledCount;
    private int noShowCount;
    private int paidBookingsCount;
    private BigDecimal totalRevenueEarned;
    private List<PractitionerPatientVisitDTO> visits;
}

