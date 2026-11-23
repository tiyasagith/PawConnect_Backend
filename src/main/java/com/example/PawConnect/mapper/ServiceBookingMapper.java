package com.example.PawConnect.mapper;

import com.example.PawConnect.dto.response.BookingSummaryResponse;
import com.example.PawConnect.dto.response.ServiceBookingResponse;
import com.example.PawConnect.model.PaymentStatus;
import com.example.PawConnect.model.ServiceBooking;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class ServiceBookingMapper {

    private final com.example.PawConnect.repository.PaymentOrderRepository paymentOrderRepository;
    private final com.example.PawConnect.repository.PaymentRepository paymentRepository;
    private final com.example.PawConnect.repository.PetVaccinationRepository petVaccinationRepository;
    private final com.example.PawConnect.repository.PetMedicalRecordRepository petMedicalRecordRepository;
    private final PetVaccinationMapper petVaccinationMapper;
    private final PetMedicalRecordMapper petMedicalRecordMapper;
    private final com.example.PawConnect.repository.ReviewRepository reviewRepository;

    public ServiceBookingMapper(
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

    private PaymentStatus resolvePaymentStatus(ServiceBooking booking) {
        if (booking == null) {
            return PaymentStatus.PENDING;
        }

        // 1. Linked Payment entity
        if (booking.getPayment() != null && booking.getPayment().getStatus() != null) {
            PaymentStatus s = booking.getPayment().getStatus();
            if (s == PaymentStatus.PAID || s == PaymentStatus.SUCCESS) {
                return PaymentStatus.PAID;
            }
            if (s == PaymentStatus.REFUNDED) {
                return PaymentStatus.REFUNDED;
            }
        }

        // 2. Check all matching PaymentOrders
        if (paymentOrderRepository != null && booking.getId() != null) {
            java.util.List<com.example.PawConnect.model.PaymentOrder> orders = paymentOrderRepository.findAllByBookingId(booking.getId());
            for (com.example.PawConnect.model.PaymentOrder po : orders) {
                PaymentStatus s = po.getStatus();
                if (s == PaymentStatus.PAID || s == PaymentStatus.SUCCESS) {
                    return PaymentStatus.PAID;
                }
                if (s == PaymentStatus.REFUNDED) {
                    return PaymentStatus.REFUNDED;
                }
            }
            var po = paymentOrderRepository.findTopByBookingIdAndBookingTypeOrderByCreatedAtDesc(booking.getId(), "SERVICE");
            if (po.isEmpty()) {
                po = paymentOrderRepository.findTopByBookingIdAndBookingTypeOrderByCreatedAtDesc(booking.getId(), "SERVICE_BOOKING");
            }
            if (po.isPresent()) {
                PaymentStatus s = po.get().getStatus();
                if (s == PaymentStatus.PAID || s == PaymentStatus.SUCCESS) {
                    return PaymentStatus.PAID;
                }
                if (s == PaymentStatus.REFUNDED) {
                    return PaymentStatus.REFUNDED;
                }
            }
        }

        // 3. Check Payment repository by referenceId
        if (paymentRepository != null && booking.getId() != null) {
            var lp = paymentRepository.findFirstByPaymentForAndReferenceIdOrderByIdDesc(com.example.PawConnect.model.PaymentFor.SERVICE_BOOKING, booking.getId());
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

        // 4. If booking is confirmed or completed, payment is considered settled
        if (booking.getStatus() == com.example.PawConnect.model.BookingStatus.CONFIRMED
                || booking.getStatus() == com.example.PawConnect.model.BookingStatus.COMPLETED) {
            return PaymentStatus.PAID;
        }

        // 5. Cancelled booking with refund
        if (booking.getStatus() == com.example.PawConnect.model.BookingStatus.CANCELLED) {
            if (booking.getPayment() != null && booking.getPayment().getStatus() == PaymentStatus.REFUNDED) {
                return PaymentStatus.REFUNDED;
            }
        }

        return booking.getPayment() != null && booking.getPayment().getStatus() != null
                ? booking.getPayment().getStatus()
                : PaymentStatus.PENDING;
    }

    public ServiceBookingResponse toResponse(ServiceBooking booking) {
        if (booking == null) {
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

        if (booking.getPet() != null) {
            petId = booking.getPet().getId();
            petName = booking.getPet().getName();
            species = booking.getPet().getSpecies();
            breed = booking.getPet().getBreed();
            petPhotoUrl = booking.getPet().getPhotoUrl();
            petGender = booking.getPet().getGender() != null ? booking.getPet().getGender().name() : null;
            petDateOfBirth = booking.getPet().getDateOfBirth();
            if (petDateOfBirth != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(petDateOfBirth, java.time.LocalDate.now());
                petAgeYears = Math.round((days / 365.25) * 10.0) / 10.0;
            }
            petWeight = booking.getPet().getWeight();
            petMicrochipNumber = booking.getPet().getMicrochipNumber();
            petNotes = booking.getPet().getNotes();

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

            if (booking.getPet().getOwner() != null) {
                ownerId = booking.getPet().getOwner().getId();
                String first = booking.getPet().getOwner().getFirstName() != null ? booking.getPet().getOwner().getFirstName() : "";
                String last = booking.getPet().getOwner().getLastName() != null ? booking.getPet().getOwner().getLastName() : "";
                ownerName = (first + " " + last).trim();
                ownerPhone = booking.getPet().getOwner().getPhone();
                ownerEmail = booking.getPet().getOwner().getEmail();
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

        Long providerId = null;
        String businessName = null;
        String providerPhone = null;
        String providerType = null;

        if (booking.getProvider() != null) {
            providerId = booking.getProvider().getId();
            businessName = booking.getProvider().getBusinessName();
            providerPhone = booking.getProvider().getPhone();
            providerType = booking.getProvider().getProviderType() != null ? booking.getProvider().getProviderType().name() : null;
        }

        Long serviceId = null;
        String serviceName = null;
        Integer durationMinutes = null;

        if (booking.getService() != null) {
            serviceId = booking.getService().getId();
            serviceName = booking.getService().getName();
            durationMinutes = booking.getService().getDurationMinutes();
        }

        PaymentStatus paymentStatus = resolvePaymentStatus(booking);

        java.time.Instant expiresAt = booking.getPaymentExpiresAt();
        Long remainingSeconds = null;
        boolean canRetry = false;
        if (paymentStatus == PaymentStatus.PAID || paymentStatus == PaymentStatus.REFUNDED
                || booking.getStatus() == com.example.PawConnect.model.BookingStatus.CONFIRMED
                || booking.getStatus() == com.example.PawConnect.model.BookingStatus.CANCELLED
                || booking.getStatus() == com.example.PawConnect.model.BookingStatus.COMPLETED) {
            expiresAt = null;
            remainingSeconds = 0L;
            canRetry = false;
        } else if (expiresAt != null) {
            remainingSeconds = Math.max(0L, java.time.Duration.between(java.time.Instant.now(), expiresAt).getSeconds());
            canRetry = (paymentStatus == PaymentStatus.PENDING || paymentStatus == PaymentStatus.FAILED)
                    && booking.getStatus() == com.example.PawConnect.model.BookingStatus.PENDING
                    && java.time.Instant.now().isBefore(expiresAt);
        }

        return ServiceBookingResponse.builder()
                .id(booking.getId())
                .bookingNo(booking.getBookingNo())
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
                .providerId(providerId)
                .businessName(businessName)
                .providerPhone(providerPhone)
                .providerType(providerType)
                .serviceId(serviceId)
                .serviceName(serviceName)
                .durationMinutes(durationMinutes)
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .price(booking.getPrice())
                .status(booking.getStatus())
                .paymentStatus(paymentStatus)
                .paymentExpiresAt(expiresAt)
                .remainingPaymentSeconds(remainingSeconds)
                .canRetryPayment(canRetry)
                .notes(booking.getNotes())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .petVaccinations(vaccinations)
                .petMedicalRecords(medicalRecords)
                .build();
    }

    public BookingSummaryResponse toSummary(ServiceBooking booking) {
        if (booking == null) {
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

        if (booking.getPet() != null) {
            petId = booking.getPet().getId();
            petName = booking.getPet().getName();
            species = booking.getPet().getSpecies();
            breed = booking.getPet().getBreed();
            petPhotoUrl = booking.getPet().getPhotoUrl();
            petGender = booking.getPet().getGender() != null ? booking.getPet().getGender().name() : null;
            petDateOfBirth = booking.getPet().getDateOfBirth();
            if (petDateOfBirth != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(petDateOfBirth, java.time.LocalDate.now());
                petAgeYears = Math.round((days / 365.25) * 10.0) / 10.0;
            }
            petWeight = booking.getPet().getWeight();
            petMicrochipNumber = booking.getPet().getMicrochipNumber();
            petNotes = booking.getPet().getNotes();

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

            if (booking.getPet().getOwner() != null) {
                ownerId = booking.getPet().getOwner().getId();
                String first = booking.getPet().getOwner().getFirstName() != null ? booking.getPet().getOwner().getFirstName() : "";
                String last = booking.getPet().getOwner().getLastName() != null ? booking.getPet().getOwner().getLastName() : "";
                ownerName = (first + " " + last).trim();
                ownerPhone = booking.getPet().getOwner().getPhone();
                ownerEmail = booking.getPet().getOwner().getEmail();
            }
        }

        Long providerId = null;
        String businessName = null;
        String providerPhone = null;
        String providerType = null;
        String providerImageUrl = null;
        String providerAddress = null;
        BigDecimal providerLatitude = null;
        BigDecimal providerLongitude = null;

        if (booking.getProvider() != null) {
            providerId = booking.getProvider().getId();
            businessName = booking.getProvider().getBusinessName();
            providerAddress = booking.getProvider().getAddress();
            providerLatitude = booking.getProvider().getLatitude();
            providerLongitude = booking.getProvider().getLongitude();
            providerPhone = booking.getProvider().getPhone();
            providerType = booking.getProvider().getProviderType() != null ? booking.getProvider().getProviderType().name() : null;
            if (booking.getProvider().getUser() != null) {
                providerImageUrl = booking.getProvider().getUser().getProfileImage();
            }
        }

        Long serviceId = null;
        String serviceName = null;

        if (booking.getService() != null) {
            serviceId = booking.getService().getId();
            serviceName = booking.getService().getName();
        }

        com.example.PawConnect.model.PaymentStatus pStatus = resolvePaymentStatus(booking);
        String paymentStatusStr = pStatus.name();

        java.time.Instant expiresAt = booking.getPaymentExpiresAt();
        Long remainingSeconds = null;
        boolean canRetry = false;
        if (pStatus == PaymentStatus.PAID || pStatus == PaymentStatus.REFUNDED
                || booking.getStatus() == com.example.PawConnect.model.BookingStatus.CONFIRMED
                || booking.getStatus() == com.example.PawConnect.model.BookingStatus.CANCELLED
                || booking.getStatus() == com.example.PawConnect.model.BookingStatus.COMPLETED) {
            expiresAt = null;
            remainingSeconds = 0L;
            canRetry = false;
        } else if (expiresAt != null) {
            remainingSeconds = Math.max(0L, java.time.Duration.between(java.time.Instant.now(), expiresAt).getSeconds());
            canRetry = (pStatus == PaymentStatus.PENDING || pStatus == PaymentStatus.FAILED)
                    && booking.getStatus() == com.example.PawConnect.model.BookingStatus.PENDING
                    && java.time.Instant.now().isBefore(expiresAt);
        }

        Boolean isReviewedSummary = false;
        Integer reviewRatingSummary = null;
        String reviewCommentSummary = null;
        java.time.LocalDateTime reviewCreatedAtSummary = null;

        if (reviewRepository != null && booking.getId() != null) {
            java.util.List<com.example.PawConnect.model.Review> revs = reviewRepository.findByServiceBookingId(booking.getId());
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
                .id(booking.getId())
                .bookingType("SERVICE")
                .referenceNo(booking.getBookingNo())
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
                .providerId(providerId)
                .providerName(businessName)
                .providerType(providerType)
                .providerPhone(providerPhone)
                .providerImageUrl(providerImageUrl)
                .clinicOrBusinessName(businessName)
                .locationAddress(providerAddress)
                .latitude(providerLatitude)
                .longitude(providerLongitude)
                .serviceId(serviceId)
                .serviceOrReason(serviceName)
                .date(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .amount(booking.getPrice())
                .status(booking.getStatus() != null ? booking.getStatus().name() : null)
                .paymentStatus(paymentStatusStr)
                .paymentExpiresAt(expiresAt)
                .remainingPaymentSeconds(remainingSeconds)
                .canRetryPayment(canRetry)
                .notes(booking.getNotes())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
