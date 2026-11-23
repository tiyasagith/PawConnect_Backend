package com.example.PawConnect.service.impl;

import com.example.PawConnect.dto.response.PractitionerPatientVisitDTO;
import com.example.PawConnect.dto.response.PractitionerPerformanceReportDTO;
import com.example.PawConnect.exception.ForbiddenException;
import com.example.PawConnect.exception.ResourceNotFoundException;
import com.example.PawConnect.model.*;
import com.example.PawConnect.repository.*;
import com.example.PawConnect.service.CurrentUserService;
import com.example.PawConnect.service.PractitionerReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PractitionerReportServiceImpl implements PractitionerReportService {

    private final VeterinarianRepository veterinarianRepository;
    private final ServiceProviderRepository serviceProviderRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceBookingRepository serviceBookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional(readOnly = true)
    public List<PractitionerPerformanceReportDTO> getAdminPractitionerActivity(
            String type,
            Long practitionerId,
            LocalDate startDate,
            LocalDate endDate,
            String status
    ) {
        String cleanType = type != null ? type.trim().toUpperCase() : "ALL";
        List<PractitionerPerformanceReportDTO> results = new ArrayList<>();

        boolean includeVets = "ALL".equals(cleanType) || "VETERINARIAN".equals(cleanType);
        boolean includeProviders = "ALL".equals(cleanType) || "SERVICE_PROVIDER".equals(cleanType)
                || "GROOMER".equals(cleanType) || "TRAINER".equals(cleanType) || "SERVICE".equals(cleanType);

        if (includeVets) {
            List<Veterinarian> vets;
            if (practitionerId != null && "VETERINARIAN".equals(cleanType)) {
                vets = veterinarianRepository.findById(practitionerId).map(List::of).orElse(Collections.emptyList());
            } else if (practitionerId != null && "ALL".equals(cleanType)) {
                vets = veterinarianRepository.findById(practitionerId).map(List::of).orElse(Collections.emptyList());
            } else {
                vets = veterinarianRepository.findAll();
            }

            for (Veterinarian vet : vets) {
                results.add(buildVeterinarianReport(vet, startDate, endDate, status));
            }
        }

        if (includeProviders) {
            List<ServiceProvider> providers;
            if (practitionerId != null && ("SERVICE_PROVIDER".equals(cleanType) || "GROOMER".equals(cleanType) || "TRAINER".equals(cleanType))) {
                providers = serviceProviderRepository.findById(practitionerId).map(List::of).orElse(Collections.emptyList());
            } else if (practitionerId != null && "ALL".equals(cleanType)) {
                // If practitionerId was not found in vets, check provider
                if (results.isEmpty()) {
                    providers = serviceProviderRepository.findById(practitionerId).map(List::of).orElse(Collections.emptyList());
                } else {
                    providers = Collections.emptyList();
                }
            } else {
                providers = serviceProviderRepository.findAll();
            }

            if ("GROOMER".equals(cleanType)) {
                providers = providers.stream()
                        .filter(p -> p.getProviderType() == ProviderType.GROOMER)
                        .toList();
            } else if ("TRAINER".equals(cleanType)) {
                providers = providers.stream()
                        .filter(p -> p.getProviderType() == ProviderType.TRAINER)
                        .toList();
            }

            for (ServiceProvider provider : providers) {
                results.add(buildProviderReport(provider, startDate, endDate, status));
            }
        }

        // Sort leaderboard by completed visits descending, then total bookings descending
        results.sort(Comparator.comparingInt(PractitionerPerformanceReportDTO::getCompletedCount).reversed()
                .thenComparing(Comparator.comparingInt(PractitionerPerformanceReportDTO::getTotalBookings).reversed()));

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public PractitionerPerformanceReportDTO getVeterinarianPatientReport(
            LocalDate startDate,
            LocalDate endDate,
            String status
    ) {
        Long userId = currentUserService.getCurrentUserId();
        Veterinarian vet = veterinarianRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Authenticated user is not registered as a veterinarian"));

        return buildVeterinarianReport(vet, startDate, endDate, status);
    }

    @Override
    @Transactional(readOnly = true)
    public PractitionerPerformanceReportDTO getServiceProviderClientReport(
            LocalDate startDate,
            LocalDate endDate,
            String status
    ) {
        Long userId = currentUserService.getCurrentUserId();
        ServiceProvider provider = serviceProviderRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Authenticated user is not registered as a service provider"));

        return buildProviderReport(provider, startDate, endDate, status);
    }

    private PractitionerPerformanceReportDTO buildVeterinarianReport(
            Veterinarian vet,
            LocalDate startDate,
            LocalDate endDate,
            String statusFilter
    ) {
        List<Appointment> allAppointments = appointmentRepository.findByVeterinarianId(vet.getId());

        List<PractitionerPatientVisitDTO> visits = new ArrayList<>();
        int completedCount = 0;
        int confirmedCount = 0;
        int pendingCount = 0;
        int cancelledCount = 0;
        int noShowCount = 0;
        int paidCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        Set<Long> uniquePetIds = new HashSet<>();

        for (Appointment apt : allAppointments) {
            LocalDate date = apt.getAppointmentDate();
            if (startDate != null && date != null && date.isBefore(startDate)) {
                continue;
            }
            if (endDate != null && date != null && date.isAfter(endDate)) {
                continue;
            }

            String currentStatus = apt.getStatus() != null ? apt.getStatus().name() : "PENDING";
            if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("ALL")) {
                if (!currentStatus.equalsIgnoreCase(statusFilter.trim())) {
                    continue;
                }
            }

            PaymentStatus pStatus = resolveAppointmentPaymentStatus(apt);
            boolean isPaid = pStatus == PaymentStatus.PAID || pStatus == PaymentStatus.SUCCESS;
            BigDecimal fee = apt.getConsultationFee() != null ? apt.getConsultationFee() : BigDecimal.ZERO;

            if (AppointmentStatus.COMPLETED.name().equals(currentStatus)) {
                completedCount++;
            } else if (AppointmentStatus.CONFIRMED.name().equals(currentStatus)) {
                confirmedCount++;
            } else if (AppointmentStatus.PENDING.name().equals(currentStatus)) {
                pendingCount++;
            } else if (AppointmentStatus.CANCELLED.name().equals(currentStatus)) {
                cancelledCount++;
            } else if (AppointmentStatus.NO_SHOW.name().equals(currentStatus)) {
                noShowCount++;
            }

            if (isPaid) {
                paidCount++;
                totalRevenue = totalRevenue.add(fee);
            }

            if (apt.getPet() != null && apt.getPet().getId() != null) {
                uniquePetIds.add(apt.getPet().getId());
            }

            User owner = apt.getPet() != null ? apt.getPet().getOwner() : null;

            visits.add(PractitionerPatientVisitDTO.builder()
                    .bookingId(apt.getId())
                    .referenceNo(apt.getAppointmentNo())
                    .bookingType("VETERINARY")
                    .visitDate(apt.getAppointmentDate())
                    .startTime(apt.getStartTime())
                    .petId(apt.getPet() != null ? apt.getPet().getId() : null)
                    .petName(apt.getPet() != null ? apt.getPet().getName() : "Pet")
                    .petSpecies(apt.getPet() != null ? apt.getPet().getSpecies() : null)
                    .petBreed(apt.getPet() != null ? apt.getPet().getBreed() : null)
                    .ownerName(owner != null ? owner.getFullName() : "Client")
                    .ownerPhone(owner != null ? owner.getPhone() : null)
                    .ownerEmail(owner != null ? owner.getEmail() : null)
                    .serviceOrReason(apt.getReason() != null ? apt.getReason() : "Clinical Consultation")
                    .status(currentStatus)
                    .paymentStatus(isPaid ? "PAID" : pStatus.name())
                    .amount(fee)
                    .clinicalOrSessionNotes(apt.getNotes())
                    .build());
        }

        // Sort visits chronologically newest first
        visits.sort(Comparator.comparing(PractitionerPatientVisitDTO::getVisitDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PractitionerPatientVisitDTO::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));

        User vetUser = vet.getUser();
        String vetName = vetUser != null ? vetUser.getFullName() : "Doctor";
        if (vetName != null && !vetName.trim().isEmpty() && !vetName.toLowerCase().startsWith("dr.") && !vetName.toLowerCase().startsWith("dr ")) {
            vetName = "Dr. " + vetName;
        }

        return PractitionerPerformanceReportDTO.builder()
                .practitionerId(vet.getId())
                .userId(vetUser != null ? vetUser.getId() : null)
                .practitionerType("VETERINARIAN")
                .subType(vet.getSpecialization() != null && !vet.getSpecialization().isBlank() ? vet.getSpecialization() : "General Veterinary Medicine")
                .name(vetName)
                .email(vetUser != null ? vetUser.getEmail() : "")
                .phone(vetUser != null ? vetUser.getPhone() : "")
                .clinicOrBusinessName(vet.getClinicName() != null ? vet.getClinicName() : "Veterinary Clinic")
                .totalBookings(visits.size())
                .uniquePetsChecked(uniquePetIds.size())
                .completedCount(completedCount)
                .confirmedCount(confirmedCount)
                .pendingCount(pendingCount)
                .cancelledCount(cancelledCount)
                .noShowCount(noShowCount)
                .paidBookingsCount(paidCount)
                .totalRevenueEarned(totalRevenue)
                .visits(visits)
                .build();
    }

    private PractitionerPerformanceReportDTO buildProviderReport(
            ServiceProvider provider,
            LocalDate startDate,
            LocalDate endDate,
            String statusFilter
    ) {
        List<ServiceBooking> allBookings = serviceBookingRepository.findByProviderId(provider.getId());

        List<PractitionerPatientVisitDTO> visits = new ArrayList<>();
        int completedCount = 0;
        int confirmedCount = 0;
        int pendingCount = 0;
        int cancelledCount = 0;
        int noShowCount = 0;
        int paidCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        Set<Long> uniquePetIds = new HashSet<>();

        String typeCategory = provider.getProviderType() == ProviderType.GROOMER ? "GROOMING"
                : provider.getProviderType() == ProviderType.TRAINER ? "TRAINING" : "SERVICE";

        for (ServiceBooking sb : allBookings) {
            LocalDate date = sb.getBookingDate();
            if (startDate != null && date != null && date.isBefore(startDate)) {
                continue;
            }
            if (endDate != null && date != null && date.isAfter(endDate)) {
                continue;
            }

            String currentStatus = sb.getStatus() != null ? sb.getStatus().name() : "PENDING";
            if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("ALL")) {
                if (!currentStatus.equalsIgnoreCase(statusFilter.trim())) {
                    continue;
                }
            }

            PaymentStatus pStatus = resolveServiceBookingPaymentStatus(sb);
            boolean isPaid = pStatus == PaymentStatus.PAID || pStatus == PaymentStatus.SUCCESS;
            BigDecimal price = sb.getPrice() != null ? sb.getPrice() : BigDecimal.ZERO;

            if (BookingStatus.COMPLETED.name().equals(currentStatus)) {
                completedCount++;
            } else if (BookingStatus.CONFIRMED.name().equals(currentStatus)) {
                confirmedCount++;
            } else if (BookingStatus.PENDING.name().equals(currentStatus)) {
                pendingCount++;
            } else if (BookingStatus.CANCELLED.name().equals(currentStatus)) {
                cancelledCount++;
            } else if (BookingStatus.NO_SHOW.name().equals(currentStatus)) {
                noShowCount++;
            }

            if (isPaid) {
                paidCount++;
                totalRevenue = totalRevenue.add(price);
            }

            if (sb.getPet() != null && sb.getPet().getId() != null) {
                uniquePetIds.add(sb.getPet().getId());
            }

            User owner = sb.getPet() != null ? sb.getPet().getOwner() : null;
            String serviceTitle = sb.getService() != null ? sb.getService().getName() : "Professional Pet Care";

            visits.add(PractitionerPatientVisitDTO.builder()
                    .bookingId(sb.getId())
                    .referenceNo(sb.getBookingNo())
                    .bookingType(typeCategory)
                    .visitDate(sb.getBookingDate())
                    .startTime(sb.getStartTime())
                    .petId(sb.getPet() != null ? sb.getPet().getId() : null)
                    .petName(sb.getPet() != null ? sb.getPet().getName() : "Pet")
                    .petSpecies(sb.getPet() != null ? sb.getPet().getSpecies() : null)
                    .petBreed(sb.getPet() != null ? sb.getPet().getBreed() : null)
                    .ownerName(owner != null ? owner.getFullName() : "Client")
                    .ownerPhone(owner != null ? owner.getPhone() : null)
                    .ownerEmail(owner != null ? owner.getEmail() : null)
                    .serviceOrReason(serviceTitle)
                    .status(currentStatus)
                    .paymentStatus(isPaid ? "PAID" : pStatus.name())
                    .amount(price)
                    .clinicalOrSessionNotes(sb.getNotes())
                    .build());
        }

        // Sort visits chronologically newest first
        visits.sort(Comparator.comparing(PractitionerPatientVisitDTO::getVisitDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PractitionerPatientVisitDTO::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));

        User user = provider.getUser();
        String providerName = user != null ? user.getFullName() : provider.getBusinessName();

        return PractitionerPerformanceReportDTO.builder()
                .practitionerId(provider.getId())
                .userId(user != null ? user.getId() : null)
                .practitionerType("SERVICE_PROVIDER")
                .subType(provider.getProviderType() != null ? provider.getProviderType().name() : "SPECIALIST")
                .name(providerName)
                .email(user != null ? user.getEmail() : "")
                .phone(provider.getPhone() != null ? provider.getPhone() : (user != null ? user.getPhone() : ""))
                .clinicOrBusinessName(provider.getBusinessName() != null ? provider.getBusinessName() : "Pet Care Facility")
                .totalBookings(visits.size())
                .uniquePetsChecked(uniquePetIds.size())
                .completedCount(completedCount)
                .confirmedCount(confirmedCount)
                .pendingCount(pendingCount)
                .cancelledCount(cancelledCount)
                .noShowCount(noShowCount)
                .paidBookingsCount(paidCount)
                .totalRevenueEarned(totalRevenue)
                .visits(visits)
                .build();
    }

    private PaymentStatus resolveAppointmentPaymentStatus(Appointment apt) {
        if (apt.getPayment() != null && apt.getPayment().getStatus() != null) {
            return apt.getPayment().getStatus();
        }
        Optional<PaymentOrder> po = paymentOrderRepository.findTopByBookingIdAndBookingTypeOrderByCreatedAtDesc(
                apt.getId(), "VETERINARY");
        if (po.isPresent() && (po.get().getStatus() == PaymentStatus.PAID || po.get().getStatus() == PaymentStatus.SUCCESS)) {
            return PaymentStatus.PAID;
        }
        Optional<Payment> p = paymentRepository.findFirstByPaymentForAndReferenceIdOrderByIdDesc(
                PaymentFor.APPOINTMENT, apt.getId());
        if (p.isPresent() && (p.get().getStatus() == PaymentStatus.PAID || p.get().getStatus() == PaymentStatus.SUCCESS)) {
            return PaymentStatus.PAID;
        }
        return PaymentStatus.PENDING;
    }

    private PaymentStatus resolveServiceBookingPaymentStatus(ServiceBooking sb) {
        if (sb.getPayment() != null && sb.getPayment().getStatus() != null) {
            return sb.getPayment().getStatus();
        }
        Optional<PaymentOrder> po = paymentOrderRepository.findTopByBookingIdAndBookingTypeOrderByCreatedAtDesc(
                sb.getId(), "SERVICE");
        if (po.isEmpty()) {
            po = paymentOrderRepository.findTopByBookingIdAndBookingTypeOrderByCreatedAtDesc(
                    sb.getId(), "SERVICE_BOOKING");
        }
        if (po.isPresent() && (po.get().getStatus() == PaymentStatus.PAID || po.get().getStatus() == PaymentStatus.SUCCESS)) {
            return PaymentStatus.PAID;
        }
        Optional<Payment> p = paymentRepository.findFirstByPaymentForAndReferenceIdOrderByIdDesc(
                PaymentFor.SERVICE_BOOKING, sb.getId());
        if (p.isPresent() && (p.get().getStatus() == PaymentStatus.PAID || p.get().getStatus() == PaymentStatus.SUCCESS)) {
            return PaymentStatus.PAID;
        }
        return PaymentStatus.PENDING;
    }
}

