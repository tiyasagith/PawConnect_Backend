package com.example.PawConnect.dto.response;

import com.example.PawConnect.model.AppointmentStatus;
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
public class AppointmentResponse {

    private Long id;
    private String appointmentNo;

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

    // Veterinarian details
    private Long veterinarianId;
    private String veterinarianName;
    private String clinicName;
    private String clinicAddress;
    private String specialization;
    private String veterinarianQualification;
    private String veterinarianLicenseNumber;

    // Schedule & details
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String reason;
    private BigDecimal consultationFee;
    private AppointmentStatus status;
    private PaymentStatus paymentStatus;
    private String notes;
    private java.time.Instant paymentExpiresAt;
    private Long remainingPaymentSeconds;
    private Boolean canRetryPayment;

    // Customer Review & Rating
    private Boolean isReviewed;
    private Integer reviewRating;
    private String reviewComment;
    private LocalDateTime reviewCreatedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Existing Health Records & Vaccinations
    private List<PetVaccinationResponse> petVaccinations;
    private List<PetMedicalRecordResponse> petMedicalRecords;
}
