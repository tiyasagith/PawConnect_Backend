package com.example.PawConnect.mapper;

import com.example.PawConnect.dto.response.AppointmentResponse;
import com.example.PawConnect.dto.response.BookingSummaryResponse;
import com.example.PawConnect.model.Appointment;
import com.example.PawConnect.model.PaymentStatus;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

    private final com.example.PawConnect.repository.PaymentOrderRepository paymentOrderRepository;
    private final com.example.PawConnect.repository.PaymentRepository paymentRepository;
    private final com.example.PawConnect.repository.PetVaccinationRepository petVaccinationRepository;
    private final com.example.PawConnect.repository.PetMedicalRecordRepository petMedicalRecordRepository;
    private final PetVaccinationMapper petVaccinationMapper;
    private final PetMedicalRecordMapper petMedicalRecordMapper;
    private final com.example.PawConnect.repository.ReviewRepository reviewRepository;

    public AppointmentMapper(
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.PawConnect.repository.PaymentOrderRepository paymentOrderRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.PawConnect.repository.PaymentRepository paymentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.PawConnect.repository.PetVaccinationRepository petVaccinationRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.PawConnect.repository.PetMedicalRecordRepository petMedicalRecordRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PetVaccinationMapper petVaccinationMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PetMedicalRecordMapper petMedicalRecordMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.example.PawConnect.repository.ReviewRepository reviewRepository
    ) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentRepository = paymentRepository;
        this.petVaccinationRepository = petVaccinationRepository;
        this.petMedicalRecordRepository = petMedicalRecordRepository;
        this.petVaccinationMapper = petVaccinationMapper;
        this.petMedicalRecordMapper = petMedicalRecordMapper;
        this.reviewRepository = reviewRepository;
    }

    private PaymentStatus resolvePaymentStatus(Appointment appointment) {
        if (appointment == null) {
            return PaymentStatus.PENDING;
        }

        // 1. Linked Payment entity
        if (appointment.getPayment() != null && appointment.getPayment().getStatus() != null) {
            PaymentStatus s = appointment.getPayment().getStatus();
            if (s == PaymentStatus.PAID || s == PaymentStatus.SUCCESS) {
                return PaymentStatus.PAID;
            }
            if (s == PaymentStatus.REFUNDED) {
                return PaymentStatus.REFUNDED;
            }
        }

        // 2. Check all matching PaymentOrders
        if (paymentOrderRepository != null && appointment.getId() != null) {
            java.util.List<com.example.PawConnect.model.PaymentOrder> orders = paymentOrderRepository.findAllByBookingId(appointment.getId());
            for (com.example.PawConnect.model.PaymentOrder po : orders) {
                PaymentStatus s = po.getStatus();
                if (s == PaymentStatus.PAID || s == PaymentStatus.SUCCESS) {
                    return PaymentStatus.PAID;
                }
                if (s == PaymentStatus.REFUNDED) {
                    return PaymentStatus.REFUNDED;
                }
            }
            var topPo = paymentOrderRepository.findTopByBookingIdAndBookingTypeOrderByCreatedAtDesc(appointment.getId(), "VETERINARY");
            if (topPo.isPresent()) {
                PaymentStatus s = topPo.get().getStatus();
                if (s == PaymentStatus.PAID || s == PaymentStatus.SUCCESS) {
                    return PaymentStatus.PAID;
                }
                if (s == PaymentStatus.REFUNDED) {
                    return PaymentStatus.REFUNDED;
                }
            }
        }

        // 3. Check Payment repository by referenceId
        if (paymentRepository != null && appointment.getId() != null) {
            var lp = paymentRepository.findFirstByPaymentForAndReferenceIdOrderByIdDesc(com.example.PawConnect.model.PaymentFor.APPOINTMENT, appointment.getId());
            if (lp.isPresent()) {
                PaymentStatus s = lp.get().getStatus();
                if (s == PaymentStatus.PAID || s == PaymentStatus.SUCCESS) {
                    return PaymentStatus.PAID;
                }
                if (s == PaymentStatus.REFUNDED) {
                    return PaymentStatus.REFUNDED;
                }
            }
        }

        // 4. If appointment is confirmed or completed, payment is considered settled
        if (appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.CONFIRMED
                || appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.COMPLETED) {
            return PaymentStatus.PAID;
        }

        // 5. Cancelled appointment with refund
        if (appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.CANCELLED) {
            if (appointment.getPayment() != null && appointment.getPayment().getStatus() == PaymentStatus.REFUNDED) {
                return PaymentStatus.REFUNDED;
            }
        }

        return appointment.getPayment() != null && appointment.getPayment().getStatus() != null
                ? appointment.getPayment().getStatus()
                : PaymentStatus.PENDING;
    }

    public AppointmentResponse toResponse(Appointment appointment) {
        if (appointment == null) {
            return null;
        }

        Long petId = null;
        String petName = null;
        String species = null;
        String breed = null;
        String petPhotoUrl = null;
        String petGender = null;
        java.time.LocalDate petDateOfBirth = null;
        Double petAgeYears = null;
        BigDecimal petWeight = null;
        String petMicrochipNumber = null;
        String petNotes = null;
        String petColor = null;
        Long ownerId = null;
        String ownerName = null;
        String ownerPhone = null;
        String ownerEmail = null;

        if (appointment.getPet() != null) {
            petId = appointment.getPet().getId();
            petName = appointment.getPet().getName();
            species = appointment.getPet().getSpecies();
            breed = appointment.getPet().getBreed();
            petPhotoUrl = appointment.getPet().getPhotoUrl();
            petGender = appointment.getPet().getGender() != null ? appointment.getPet().getGender().name() : null;
            petDateOfBirth = appointment.getPet().getDateOfBirth();
            if (petDateOfBirth != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(petDateOfBirth, java.time.LocalDate.now());
                petAgeYears = Math.round((days / 365.25) * 10.0) / 10.0;
            }
            petWeight = appointment.getPet().getWeight();
            petMicrochipNumber = appointment.getPet().getMicrochipNumber();
            petNotes = appointment.getPet().getNotes();

            if ((petMicrochipNumber == null || petMicrochipNumber.isBlank()) && petNotes != null) {
                java.util.regex.Matcher chipMatcher = java.util.regex.Pattern.compile("(?i)Microchip:\\s*([^;,\n\r]+)").matcher(petNotes);
                if (chipMatcher.find()) {
                    petMicrochipNumber = chipMatcher.group(1).trim();
                }
            }

            if (petNotes != null) {
                java.util.regex.Matcher colorMatcher = java.util.regex.Pattern.compile("(?i)Color:\\s*([^;,\n\r]+)").matcher(petNotes);
                if (colorMatcher.find()) {
                    petColor = colorMatcher.group(1).trim();
                }
            }

            if (appointment.getPet().getOwner() != null) {
                ownerId = appointment.getPet().getOwner().getId();
                String first = appointment.getPet().getOwner().getFirstName() != null ? appointment.getPet().getOwner().getFirstName() : "";
                String last = appointment.getPet().getOwner().getLastName() != null ? appointment.getPet().getOwner().getLastName() : "";
                ownerName = (first + " " + last).trim();
                ownerPhone = appointment.getPet().getOwner().getPhone();
                ownerEmail = appointment.getPet().getOwner().getEmail();
            }
        }

        java.util.List<com.example.PawConnect.dto.response.PetVaccinationResponse> vaccinations = java.util.List.of();
        java.util.List<com.example.PawConnect.dto.response.PetMedicalRecordResponse> medicalRecords = java.util.List.of();
        if (petVaccinationRepository != null && petVaccinationMapper != null && petId != null) {
            vaccinations = petVaccinationRepository.findByPetIdOrderByVaccinationDateDesc(petId).stream()
                    .map(petVaccinationMapper::toResponse)
                    .collect(java.util.stream.Collectors.toList());
        }
        if (petMedicalRecordRepository != null && petMedicalRecordMapper != null && petId != null) {
            medicalRecords = petMedicalRecordRepository.findByPetIdOrderByRecordDateDesc(petId).stream()
                    .map(petMedicalRecordMapper::toResponse)
                    .collect(java.util.stream.Collectors.toList());
        }

        Long vetId = null;
        String vetName = null;
        String clinicName = null;
        String clinicAddress = null;
        String specialization = null;
        String vetQualification = null;
        String vetLicenseNumber = null;

        if (appointment.getVeterinarian() != null) {
            vetId = appointment.getVeterinarian().getId();
            clinicName = appointment.getVeterinarian().getClinicName();
            clinicAddress = appointment.getVeterinarian().getClinicAddress();
            specialization = appointment.getVeterinarian().getSpecialization();
            vetQualification = appointment.getVeterinarian().getQualification();
            vetLicenseNumber = appointment.getVeterinarian().getLicenseNumber();

            if (appointment.getVeterinarian().getUser() != null) {
                String first = appointment.getVeterinarian().getUser().getFirstName() != null ? appointment.getVeterinarian().getUser().getFirstName() : "";
                String last = appointment.getVeterinarian().getUser().getLastName() != null ? appointment.getVeterinarian().getUser().getLastName() : "";
                vetName = (first + " " + last).trim();
            }
        }

        PaymentStatus paymentStatus = resolvePaymentStatus(appointment);

        java.time.Instant expiresAt = appointment.getPaymentExpiresAt();
        Long remainingSeconds = null;
        boolean canRetry = false;
        if (paymentStatus == PaymentStatus.PAID || paymentStatus == PaymentStatus.REFUNDED
                || appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.CONFIRMED
                || appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.CANCELLED
                || appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.COMPLETED) {
            expiresAt = null;
            remainingSeconds = 0L;
            canRetry = false;
        } else if (expiresAt != null) {
            remainingSeconds = Math.max(0L, java.time.Duration.between(java.time.Instant.now(), expiresAt).getSeconds());
            canRetry = (paymentStatus == PaymentStatus.PENDING || paymentStatus == PaymentStatus.FAILED)
                    && appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.PENDING
                    && java.time.Instant.now().isBefore(expiresAt);
        }

        Boolean isReviewed = false;
        Integer reviewRating = null;
        String reviewComment = null;
        java.time.LocalDateTime reviewCreatedAt = null;

        if (reviewRepository != null && appointment.getId() != null) {
            java.util.List<com.example.PawConnect.model.Review> revs = reviewRepository.findByAppointmentId(appointment.getId());
            if (!revs.isEmpty()) {
                com.example.PawConnect.model.Review r = revs.get(0);
                isReviewed = true;
                reviewRating = r.getRating();
                reviewComment = r.getReviewText();
                reviewCreatedAt = r.getCreatedAt();
            }
        }

        return AppointmentResponse.builder()
                .isReviewed(isReviewed)
                .reviewRating(reviewRating)
                .reviewComment(reviewComment)
                .reviewCreatedAt(reviewCreatedAt)
                .id(appointment.getId())
                .appointmentNo(appointment.getAppointmentNo())
                .petId(petId)
                .petName(petName)
                .species(species)
                .breed(breed)
                .petPhotoUrl(petPhotoUrl)
                .petGender(petGender)
                .petDateOfBirth(petDateOfBirth)
                .petAgeYears(petAgeYears)
                .petWeight(petWeight)
                .petColor(petColor)
                .petMicrochipNumber(petMicrochipNumber)
                .petNotes(petNotes)
                .ownerId(ownerId)
                .ownerName(ownerName)
                .ownerPhone(ownerPhone)
                .ownerEmail(ownerEmail)
                .veterinarianId(vetId)
                .veterinarianName(vetName)
                .clinicName(clinicName)
                .clinicAddress(clinicAddress)
                .specialization(specialization)
                .veterinarianQualification(vetQualification)
                .veterinarianLicenseNumber(vetLicenseNumber)
                .appointmentDate(appointment.getAppointmentDate())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .reason(appointment.getReason())
                .consultationFee(appointment.getConsultationFee())
                .status(appointment.getStatus())
                .paymentStatus(paymentStatus)
                .paymentExpiresAt(expiresAt)
                .remainingPaymentSeconds(remainingSeconds)
                .canRetryPayment(canRetry)
                .notes(appointment.getNotes())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .petVaccinations(vaccinations)
                .petMedicalRecords(medicalRecords)
                .build();
    }

    public BookingSummaryResponse toSummary(Appointment appointment) {
        if (appointment == null) {
            return null;
        }

        Long petId = null;
        String petName = null;
        String species = null;
        String breed = null;
        String petPhotoUrl = null;
        String petGender = null;
        java.time.LocalDate petDateOfBirth = null;
        Double petAgeYears = null;
        BigDecimal petWeight = null;
        String petColor = null;
        String petMicrochipNumber = null;
        String petNotes = null;
        Long ownerId = null;
        String ownerName = null;
        String ownerPhone = null;
        String ownerEmail = null;

        if (appointment.getPet() != null) {
            petId = appointment.getPet().getId();
            petName = appointment.getPet().getName();
            species = appointment.getPet().getSpecies();
            breed = appointment.getPet().getBreed();
            petPhotoUrl = appointment.getPet().getPhotoUrl();
            petGender = appointment.getPet().getGender() != null ? appointment.getPet().getGender().name() : null;
            petDateOfBirth = appointment.getPet().getDateOfBirth();
            if (petDateOfBirth != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(petDateOfBirth, java.time.LocalDate.now());
                petAgeYears = Math.round((days / 365.25) * 10.0) / 10.0;
            }
            petWeight = appointment.getPet().getWeight();
            petMicrochipNumber = appointment.getPet().getMicrochipNumber();
            petNotes = appointment.getPet().getNotes();

            if ((petMicrochipNumber == null || petMicrochipNumber.isBlank()) && petNotes != null) {
                java.util.regex.Matcher chipMatcher = java.util.regex.Pattern.compile("(?i)Microchip:\\s*([^;,\n\r]+)").matcher(petNotes);
                if (chipMatcher.find()) {
                    petMicrochipNumber = chipMatcher.group(1).trim();
                }
            }

            if (petNotes != null) {
                java.util.regex.Matcher colorMatcher = java.util.regex.Pattern.compile("(?i)Color:\\s*([^;,\n\r]+)").matcher(petNotes);
                if (colorMatcher.find()) {
                    petColor = colorMatcher.group(1).trim();
                }
            }

            if (appointment.getPet().getOwner() != null) {
                ownerId = appointment.getPet().getOwner().getId();
                String first = appointment.getPet().getOwner().getFirstName() != null ? appointment.getPet().getOwner().getFirstName() : "";
                String last = appointment.getPet().getOwner().getLastName() != null ? appointment.getPet().getOwner().getLastName() : "";
                ownerName = (first + " " + last).trim();
                ownerPhone = appointment.getPet().getOwner().getPhone();
                ownerEmail = appointment.getPet().getOwner().getEmail();
            }
        }

        Long vetId = null;
        String vetName = null;
        String vetPhone = null;
        String providerImageUrl = null;
        String clinicName = null;
        String clinicAddress = null;
        BigDecimal clinicLatitude = null;
        BigDecimal clinicLongitude = null;
        String qualification = null;
        String specialization = null;
        String licenseNumber = null;

        if (appointment.getVeterinarian() != null) {
            vetId = appointment.getVeterinarian().getId();
            clinicName = appointment.getVeterinarian().getClinicName();
            clinicAddress = appointment.getVeterinarian().getClinicAddress();
            clinicLatitude = appointment.getVeterinarian().getClinicLatitude();
            clinicLongitude = appointment.getVeterinarian().getClinicLongitude();
            qualification = appointment.getVeterinarian().getQualification();
            specialization = appointment.getVeterinarian().getSpecialization();
            licenseNumber = appointment.getVeterinarian().getLicenseNumber();
            if (appointment.getVeterinarian().getUser() != null) {
                String first = appointment.getVeterinarian().getUser().getFirstName() != null ? appointment.getVeterinarian().getUser().getFirstName() : "";
                String last = appointment.getVeterinarian().getUser().getLastName() != null ? appointment.getVeterinarian().getUser().getLastName() : "";
                vetName = (first + " " + last).trim();
                vetPhone = appointment.getVeterinarian().getUser().getPhone();
                providerImageUrl = appointment.getVeterinarian().getUser().getProfileImage();
            }
        }

        com.example.PawConnect.model.PaymentStatus pStatus = resolvePaymentStatus(appointment);
        String paymentStatusStr = pStatus.name();

        java.time.Instant expiresAt = appointment.getPaymentExpiresAt();
        Long remainingSeconds = null;
        boolean canRetry = false;
        if (pStatus == PaymentStatus.PAID || pStatus == PaymentStatus.REFUNDED
                || appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.CONFIRMED
                || appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.CANCELLED
                || appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.COMPLETED) {
            expiresAt = null;
            remainingSeconds = 0L;
            canRetry = false;
        } else if (expiresAt != null) {
            remainingSeconds = Math.max(0L, java.time.Duration.between(java.time.Instant.now(), expiresAt).getSeconds());
            canRetry = (pStatus == PaymentStatus.PENDING || pStatus == PaymentStatus.FAILED)
                    && appointment.getStatus() == com.example.PawConnect.model.AppointmentStatus.PENDING
                    && java.time.Instant.now().isBefore(expiresAt);
        }

        java.util.List<com.example.PawConnect.dto.response.PetMedicalRecordResponse> medicalRecords = java.util.List.of();
        if (petMedicalRecordRepository != null && petMedicalRecordMapper != null && petId != null) {
            medicalRecords = petMedicalRecordRepository.findByPetIdOrderByRecordDateDesc(petId).stream()
                    .map(petMedicalRecordMapper::toResponse)
                    .collect(java.util.stream.Collectors.toList());
        }

        Boolean isReviewedSummary = false;
        Integer reviewRatingSummary = null;
        String reviewCommentSummary = null;
        java.time.LocalDateTime reviewCreatedAtSummary = null;

        if (reviewRepository != null && appointment.getId() != null) {
            java.util.List<com.example.PawConnect.model.Review> revs = reviewRepository.findByAppointmentId(appointment.getId());
            if (!revs.isEmpty()) {
                com.example.PawConnect.model.Review r = revs.get(0);
                isReviewedSummary = true;
                reviewRatingSummary = r.getRating();
                reviewCommentSummary = r.getReviewText();
                reviewCreatedAtSummary = r.getCreatedAt();
            }
        }

        return BookingSummaryResponse.builder()
                .isReviewed(isReviewedSummary)
                .reviewRating(reviewRatingSummary)
                .reviewComment(reviewCommentSummary)
                .reviewCreatedAt(reviewCreatedAtSummary)
                .id(appointment.getId())
                .bookingType("VETERINARY")
                .referenceNo(appointment.getAppointmentNo())
                .petId(petId)
                .petName(petName)
                .species(species)
                .breed(breed)
                .petPhotoUrl(petPhotoUrl)
                .petGender(petGender)
                .petDateOfBirth(petDateOfBirth)
                .petAgeYears(petAgeYears)
                .petWeight(petWeight)
                .petColor(petColor)
                .petMicrochipNumber(petMicrochipNumber)
                .petNotes(petNotes)
                .ownerId(ownerId)
                .ownerName(ownerName)
                .ownerPhone(ownerPhone)
                .ownerEmail(ownerEmail)
                .providerId(vetId)
                .providerName(vetName)
                .providerType("VETERINARIAN")
                .providerPhone(vetPhone)
                .providerImageUrl(providerImageUrl)
                .qualification(qualification)
                .specialization(specialization)
                .licenseNumber(licenseNumber)
                .clinicOrBusinessName(clinicName)
                .locationAddress(clinicAddress)
                .latitude(clinicLatitude)
                .longitude(clinicLongitude)
                .serviceId(null)
                .serviceOrReason(appointment.getReason())
                .date(appointment.getAppointmentDate())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .amount(appointment.getConsultationFee())
                .status(appointment.getStatus() != null ? appointment.getStatus().name() : null)
                .paymentStatus(paymentStatusStr)
                .paymentExpiresAt(expiresAt)
                .remainingPaymentSeconds(remainingSeconds)
                .canRetryPayment(canRetry)
                .notes(appointment.getNotes())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .petMedicalRecords(medicalRecords)
                .build();
    }
}
