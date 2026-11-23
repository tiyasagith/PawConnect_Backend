package com.example.PawConnect.service.impl;

import com.example.PawConnect.dto.ServiceDto;
import com.example.PawConnect.dto.request.CreateReviewRequest;
import com.example.PawConnect.dto.response.ProviderSlotsResponse;
import com.example.PawConnect.dto.response.ProviderTimeSlotResponse;
import com.example.PawConnect.dto.response.PublicReviewResponse;
import com.example.PawConnect.dto.response.PublicServiceProviderResponse;
import com.example.PawConnect.dto.response.PublicVeterinarianResponse;
import com.example.PawConnect.exception.BusinessRuleException;
import com.example.PawConnect.exception.DuplicateResourceException;
import com.example.PawConnect.exception.ResourceNotFoundException;
import com.example.PawConnect.model.*;
import com.example.PawConnect.repository.*;
import com.example.PawConnect.response.PageResponse;
import com.example.PawConnect.service.CurrentUserService;
import com.example.PawConnect.service.PublicDiscoveryService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDiscoveryServiceImpl implements PublicDiscoveryService {

    private final VeterinarianRepository veterinarianRepository;
    private final ServiceProviderRepository serviceProviderRepository;
    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceBookingRepository serviceBookingRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ServiceRepository serviceRepository;
    private final ServiceProviderAvailabilityRepository serviceProviderAvailabilityRepository;
    private final VeterinarianAvailabilityRepository veterinarianAvailabilityRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PublicVeterinarianResponse> getVeterinarians(String search, String specialization, Pageable pageable) {
        Specification<Veterinarian> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("verificationStatus"), VeterinarianVerificationStatus.APPROVED));
            predicates.add(cb.equal(root.get("isActive"), true));

            if (specialization != null && !specialization.isBlank() && !specialization.equalsIgnoreCase("All Specialties")) {
                predicates.add(cb.like(cb.lower(root.get("specialization")), "%" + specialization.trim().toLowerCase() + "%"));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Join<Veterinarian, User> userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("specialization")), pattern),
                        cb.like(cb.lower(root.get("clinicName")), pattern),
                        cb.like(cb.lower(root.get("clinicAddress")), pattern),
                        cb.like(cb.lower(userJoin.get("firstName")), pattern),
                        cb.like(cb.lower(userJoin.get("lastName")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Veterinarian> page = veterinarianRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::mapVeterinarian));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicVeterinarianResponse getVeterinarianById(Long id) {
        Veterinarian vet = veterinarianRepository.findById(id)
                .filter(v -> v.getVerificationStatus() == VeterinarianVerificationStatus.APPROVED && Boolean.TRUE.equals(v.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Veterinarian not found with id: " + id));
        return mapVeterinarian(vet);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PublicServiceProviderResponse> getServiceProviders(String search, ProviderType providerType, Pageable pageable) {
        Specification<ServiceProvider> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("verificationStatus"), ServiceProviderVerificationStatus.APPROVED));
            predicates.add(cb.equal(root.get("isActive"), true));

            if (providerType != null) {
                predicates.add(cb.equal(root.get("providerType"), providerType));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Join<ServiceProvider, User> userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("businessName")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("address")), pattern),
                        cb.like(cb.lower(userJoin.get("firstName")), pattern),
                        cb.like(cb.lower(userJoin.get("lastName")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ServiceProvider> page = serviceProviderRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::mapServiceProvider));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicServiceProviderResponse getServiceProviderById(Long id) {
        ServiceProvider provider = serviceProviderRepository.findById(id)
                .filter(p -> p.getVerificationStatus() == ServiceProviderVerificationStatus.APPROVED && Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Service provider not found with id: " + id));
        return mapServiceProvider(provider);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PublicReviewResponse> getReviews(Long veterinarianId, Long providerId, Long productId, Pageable pageable) {
        Page<Review> page;
        if (veterinarianId != null) {
            page = reviewRepository.findVisibleByVeterinarianId(veterinarianId, pageable);
        } else if (providerId != null) {
            page = reviewRepository.findVisibleByProviderId(providerId, pageable);
        } else if (productId != null) {
            page = reviewRepository.findVisibleByProductId(productId, pageable);
        } else {
            page = reviewRepository.findByStatus(ReviewStatus.VISIBLE, pageable);
        }

        return PageResponse.from(page.map(this::mapReview));
    }

    @Override
    @Transactional
    public PublicReviewResponse submitReview(CreateReviewRequest request) {
        Long userId = currentUserService.getCurrentUserId();
        User user = currentUserService.getCurrentUser();

        Review.ReviewBuilder builder = Review.builder()
                .user(user)
                .rating(request.getRating())
                .reviewText(request.getReviewText().trim())
                .status(ReviewStatus.VISIBLE);

        if (request.getAppointmentId() != null) {
            Appointment appt = appointmentRepository.findById(request.getAppointmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + request.getAppointmentId()));
            if (!appt.getPet().getOwner().getId().equals(userId)) {
                throw new IllegalArgumentException("You can only review your own appointments");
            }
            if (appt.getStatus() != AppointmentStatus.COMPLETED) {
                throw new IllegalArgumentException("Only completed appointments can be reviewed");
            }
            java.util.List<Review> existing = reviewRepository.findByAppointmentId(appt.getId());
            if (!existing.isEmpty()) {
                Review prev = existing.get(0);
                if (prev.getUser().getId().equals(userId)) {
                    prev.setRating(request.getRating());
                    prev.setReviewText(request.getReviewText().trim());
                    prev.setStatus(ReviewStatus.VISIBLE);
                    Review updated = reviewRepository.save(prev);
                    log.info("User {} updated review id {} for appointment {}", userId, updated.getId(), appt.getId());
                    return mapReview(updated);
                }
                throw new DuplicateResourceException("You have already reviewed this appointment");
            }
            builder.appointment(appt);
        } else if (request.getServiceBookingId() != null) {
            ServiceBooking booking = serviceBookingRepository.findById(request.getServiceBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service booking not found with id: " + request.getServiceBookingId()));
            if (!booking.getPet().getOwner().getId().equals(userId)) {
                throw new IllegalArgumentException("You can only review your own service bookings");
            }
            if (booking.getStatus() != BookingStatus.COMPLETED) {
                throw new IllegalArgumentException("Only completed service bookings can be reviewed");
            }
            java.util.List<Review> existing = reviewRepository.findByServiceBookingId(booking.getId());
            if (!existing.isEmpty()) {
                Review prev = existing.get(0);
                if (prev.getUser().getId().equals(userId)) {
                    prev.setRating(request.getRating());
                    prev.setReviewText(request.getReviewText().trim());
                    prev.setStatus(ReviewStatus.VISIBLE);
                    Review updated = reviewRepository.save(prev);
                    log.info("User {} updated review id {} for service booking {}", userId, updated.getId(), booking.getId());
                    return mapReview(updated);
                }
                throw new DuplicateResourceException("You have already reviewed this service booking");
            }
            builder.serviceBooking(booking);
        } else if (request.getOrderItemId() != null) {
            OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + request.getOrderItemId()));
            if (!orderItem.getOrder().getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("You can only review items from your own orders");
            }
            if (orderItem.getOrder().getOrderStatus() != OrderStatus.DELIVERED) {
                throw new IllegalArgumentException("Only delivered order items can be reviewed");
            }
            if (!reviewRepository.findByOrderItemId(orderItem.getId()).isEmpty()) {
                throw new DuplicateResourceException("You have already reviewed this ordered item");
            }
            builder.orderItem(orderItem);
        } else {
            throw new IllegalArgumentException("A valid appointment, service booking, or order item is required to submit a review");
        }

        Review saved = reviewRepository.save(builder.build());
        log.info("User {} submitted review id {}", userId, saved.getId());
        return mapReview(saved);
    }

    private PublicVeterinarianResponse mapVeterinarian(Veterinarian vet) {
        String fullName = vet.getUser() != null
                ? ((vet.getUser().getFirstName() != null ? vet.getUser().getFirstName() : "") + " " +
                   (vet.getUser().getLastName() != null ? vet.getUser().getLastName() : "")).trim()
                : "Veterinarian";

        Double avgRating = reviewRepository.getAverageRatingForVeterinarian(vet.getId());
        Long reviewCount = reviewRepository.countReviewsForVeterinarian(vet.getId());

        double rating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
        long count = reviewCount != null ? reviewCount : 0L;

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        boolean isAvailableToday = false;
        var todayAvailOpt = veterinarianAvailabilityRepository.findByVeterinarianIdAndDayOfWeek(vet.getId(), today);
        if (todayAvailOpt.isPresent()) {
            VeterinarianAvailability avail = todayAvailOpt.get();
            isAvailableToday = Boolean.TRUE.equals(avail.getIsConfigured()) && Boolean.TRUE.equals(avail.getIsAvailable());
        } else if (Boolean.TRUE.equals(vet.getIsActive()) && vet.getVerificationStatus() == VeterinarianVerificationStatus.APPROVED) {
            isAvailableToday = today != DayOfWeek.SUNDAY;
        }

        return PublicVeterinarianResponse.builder()
                .id(vet.getId())
                .name(fullName)
                .email(vet.getUser() != null ? vet.getUser().getEmail() : null)
                .phone(vet.getUser() != null ? vet.getUser().getPhone() : null)
                .specialization(vet.getSpecialization())
                .qualification(vet.getQualification())
                .licenseNumber(vet.getLicenseNumber())
                .experienceYears(vet.getExperienceYears())
                .clinicName(vet.getClinicName())
                .clinicAddress(vet.getClinicAddress())
                .clinicLatitude(vet.getClinicLatitude())
                .clinicLongitude(vet.getClinicLongitude())
                .consultationFee(vet.getConsultationFee())
                .bio(vet.getBio())
                .rating(rating)
                .reviewCount(count)
                .isAvailableToday(isAvailableToday)
                .profileImageUrl(vet.getUser() != null ? vet.getUser().getProfileImage() : null)
                .build();
    }

    private PublicServiceProviderResponse mapServiceProvider(ServiceProvider provider) {
        String fullName = provider.getUser() != null
                ? ((provider.getUser().getFirstName() != null ? provider.getUser().getFirstName() : "") + " " +
                   (provider.getUser().getLastName() != null ? provider.getUser().getLastName() : "")).trim()
                : provider.getBusinessName();

        Double avgRating = reviewRepository.getAverageRatingForProvider(provider.getId());
        Long reviewCount = reviewRepository.countReviewsForProvider(provider.getId());

        double rating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
        long count = reviewCount != null ? reviewCount : 0L;

        return PublicServiceProviderResponse.builder()
                .id(provider.getId())
                .name(fullName)
                .providerType(provider.getProviderType())
                .businessName(provider.getBusinessName())
                .description(provider.getDescription())
                .phone(provider.getPhone())
                .address(provider.getAddress())
                .latitude(provider.getLatitude())
                .longitude(provider.getLongitude())
                .experienceYears(provider.getExperienceYears())
                .rating(rating)
                .reviewCount(count)
                .profileImageUrl(provider.getUser() != null ? provider.getUser().getProfileImage() : null)
                .build();
    }

    private PublicReviewResponse mapReview(Review review) {
        String reviewerName = "Anonymous";
        if (review.getUser() != null) {
            String first = review.getUser().getFirstName() != null ? review.getUser().getFirstName() : "";
            String last = review.getUser().getLastName() != null && !review.getUser().getLastName().isBlank()
                    ? review.getUser().getLastName().substring(0, 1) + "."
                    : "";
            reviewerName = (first + " " + last).trim();
            if (reviewerName.isBlank()) reviewerName = "Pet Parent";
        }

        String targetType = null;
        Long targetId = null;
        String targetTitle = null;

        if (review.getAppointment() != null && review.getAppointment().getVeterinarian() != null) {
            targetType = "VETERINARIAN";
            targetId = review.getAppointment().getVeterinarian().getId();
            targetTitle = review.getAppointment().getVeterinarian().getClinicName();
        } else if (review.getServiceBooking() != null && review.getServiceBooking().getProvider() != null) {
            targetType = "SERVICE_PROVIDER";
            targetId = review.getServiceBooking().getProvider().getId();
            targetTitle = review.getServiceBooking().getProvider().getBusinessName();
        } else if (review.getOrderItem() != null && review.getOrderItem().getProduct() != null) {
            targetType = "PRODUCT";
            targetId = review.getOrderItem().getProduct().getId();
            targetTitle = review.getOrderItem().getProduct().getName();
        }

        return PublicReviewResponse.builder()
                .id(review.getId())
                .reviewerName(reviewerName)
                .rating(review.getRating())
                .reviewText(review.getReviewText())
                .createdAt(review.getCreatedAt())
                .targetType(targetType)
                .targetId(targetId)
                .targetTitle(targetTitle)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceDto> getProviderServices(Long providerId) {
        ServiceProvider provider = serviceProviderRepository.findById(providerId)
                .filter(p -> p.getVerificationStatus() == ServiceProviderVerificationStatus.APPROVED && Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Service provider not found or not eligible with id: " + providerId));

        return serviceRepository.findByProviderIdAndIsActiveTrue(provider.getId()).stream()
                .map(s -> ServiceDto.builder()
                        .id(s.getId())
                        .providerId(provider.getId())
                        .categoryName(provider.getProviderType() == com.example.PawConnect.model.ProviderType.TRAINER ? "Training" : "Grooming")
                        .serviceType(provider.getProviderType() == com.example.PawConnect.model.ProviderType.TRAINER ? "TRAINING" : "GROOMING")
                        .name(s.getName())
                        .description(s.getDescription())
                        .price(s.getPrice())
                        .durationMinutes(s.getDurationMinutes())
                        .imageUrl(s.getImageUrl())
                        .imageUrls(s.getGalleryImageUrls())
                        .isActive(s.getIsActive())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderSlotsResponse getProviderAvailableSlots(Long providerId, Long serviceId, LocalDate date) {
        ServiceProvider provider = serviceProviderRepository.findById(providerId)
                .filter(p -> p.getVerificationStatus() == ServiceProviderVerificationStatus.APPROVED && Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Service provider not found or not eligible with id: " + providerId));

        com.example.PawConnect.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + serviceId));

        if (!service.getProvider().getId().equals(provider.getId())) {
            throw new BusinessRuleException("Selected service does not belong to the specified service provider");
        }
        if (!Boolean.TRUE.equals(service.getIsActive())) {
            throw new BusinessRuleException("Selected service is inactive");
        }

        if (service.getDurationMinutes() == null || service.getDurationMinutes() <= 0) {
            throw new BusinessRuleException("Selected service has no valid duration configured");
        }

        int serviceDuration = service.getDurationMinutes();

        var scheduleOpt = serviceProviderAvailabilityRepository.findByProviderIdAndDayOfWeekAndIsConfiguredTrue(
                providerId, date.getDayOfWeek()
        );

        if (scheduleOpt.isEmpty() || !Boolean.TRUE.equals(scheduleOpt.get().getIsAvailable()) || scheduleOpt.get().getStartTime() == null || scheduleOpt.get().getEndTime() == null) {
            return ProviderSlotsResponse.builder()
                    .providerId(providerId)
                    .serviceId(serviceId)
                    .date(date)
                    .serviceDurationMinutes(serviceDuration)
                    .slotDurationMinutes(scheduleOpt.map(ServiceProviderAvailability::getSlotDurationMinutes).orElse(30))
                    .slots(List.of())
                    .build();
        }

        ServiceProviderAvailability availability = scheduleOpt.get();
        int slotInterval = availability.getSlotDurationMinutes() != null ? availability.getSlotDurationMinutes() : 30;

        List<ServiceBooking> bookings = serviceBookingRepository.findByProviderIdAndBookingDate(providerId, date);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<ProviderTimeSlotResponse> slots = new ArrayList<>();

        for (LocalTime start = availability.getStartTime();
             !start.plusMinutes(serviceDuration).isAfter(availability.getEndTime());
             start = start.plusMinutes(slotInterval)) {
            LocalTime slotStart = start;
            LocalTime slotEnd = slotStart.plusMinutes(serviceDuration);

            boolean notInPast = date.isAfter(today) || (date.isEqual(today) && slotStart.isAfter(now));
            boolean isAvailable = notInPast && bookings.stream().noneMatch(b ->
                    b.getStatus() != BookingStatus.CANCELLED
                            && b.getStatus() != BookingStatus.NO_SHOW
                            && b.getStartTime().isBefore(slotEnd)
                            && b.getEndTime().isAfter(slotStart)
            );

            slots.add(ProviderTimeSlotResponse.builder()
                    .startTime(slotStart)
                    .endTime(slotEnd)
                    .available(isAvailable)
                    .build());
        }

        return ProviderSlotsResponse.builder()
                .providerId(providerId)
                .serviceId(serviceId)
                .date(date)
                .serviceDurationMinutes(serviceDuration)
                .slotDurationMinutes(slotInterval)
                .slots(slots)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.example.PawConnect.dto.ServiceCategoryDto> getServiceCategories() {
        return serviceCategoryRepository.findByIsActiveTrue().stream()
                .map(cat -> com.example.PawConnect.dto.ServiceCategoryDto.builder()
                        .id(cat.getId())
                        .name(cat.getName())
                        .serviceType(cat.getServiceType())
                        .description(cat.getDescription())
                        .isActive(cat.getIsActive())
                        .build())
                .toList();
    }
}
