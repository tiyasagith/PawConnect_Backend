package com.example.PawConnect.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PractitionerPatientVisitDTO {
    private Long bookingId;
    private String referenceNo;
    private String bookingType; // "VETERINARY", "GROOMING", "TRAINING"
    private LocalDate visitDate;
    private LocalTime startTime;
    private Long petId;
    private String petName;
    private String petSpecies;
    private String petBreed;
    private String ownerName;
    private String ownerPhone;
    private String ownerEmail;
    private String serviceOrReason;
    private String status;
    private String paymentStatus;
    private BigDecimal amount;
    private String clinicalOrSessionNotes;
}

