package com.example.PawConnect.dto.response;

import com.example.PawConnect.model.BookingStatus;
import com.example.PawConnect.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceBookingResponse {

    private Long id;
    private String bookingNo;

    // Pet details
    private Long petId;
    private String petName;
    private String species;
    private String breed;
    private String petPhotoUrl;
    private String petGender;
    private LocalDate petDateOfBirth;
    private Double petAgeYears;
    private BigDecimal petWeight;
    private String petColor;
    private String petMicrochipNumber;
    private String petNotes;

    // Owner details
    private Long ownerId;
    private String ownerName;
    private String ownerPhone;
    private String ownerEmail;

    // Provider details
    private Long providerId;
    private String businessName;
    private String providerPhone;
    private String providerType;

    // Service details
    private Long serviceId;
    private String serviceName;
    private Integer durationMinutes;

    // Schedule & details
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
    private BookingStatus status;
    private PaymentStatus paymentStatus;
    private String notes;
    private java.time.Instant paymentExpiresAt;
    private Long remainingPaymentSeconds;
    private Boolean canRetryPayment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Existing Health Records & Vaccinations
    private List<PetVaccinationResponse> petVaccinations;
    private List<PetMedicalRecordResponse> petMedicalRecords;
}
