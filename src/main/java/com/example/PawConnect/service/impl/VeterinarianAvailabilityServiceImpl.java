package com.example.PawConnect.service.impl;

import com.example.PawConnect.dto.VeterinarianAvailabilityDto;
import com.example.PawConnect.dto.request.professional.UpdateAvailabilityRequest;
import com.example.PawConnect.dto.response.VeterinarianSlotsResponse;
import com.example.PawConnect.dto.response.VeterinarianTimeSlotResponse;
import com.example.PawConnect.exception.BusinessRuleException;
import com.example.PawConnect.exception.ResourceNotFoundException;
import com.example.PawConnect.model.Appointment;
import com.example.PawConnect.model.AppointmentStatus;
import com.example.PawConnect.model.Veterinarian;
import com.example.PawConnect.model.VeterinarianAvailability;
import com.example.PawConnect.model.VeterinarianVerificationStatus;
import com.example.PawConnect.repository.AppointmentRepository;
import com.example.PawConnect.repository.VeterinarianAvailabilityRepository;
import com.example.PawConnect.repository.VeterinarianRepository;
import com.example.PawConnect.service.CurrentUserService;
import com.example.PawConnect.service.VeterinarianAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VeterinarianAvailabilityServiceImpl implements VeterinarianAvailabilityService {

    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(17, 0);
    private static final int DEFAULT_SLOT_DURATION_MINUTES = 30;

    private final CurrentUserService currentUserService;
    private final VeterinarianRepository veterinarianRepository;
    private final VeterinarianAvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VeterinarianAvailabilityDto> getCurrentVeterinarianAvailability() {
        Veterinarian veterinarian = getCurrentVeterinarian();
        return toWeeklySchedule(veterinarian.getId());
    }

    @Override
    @Transactional
    public List<VeterinarianAvailabilityDto> updateCurrentVeterinarianAvailability(
            List<UpdateAvailabilityRequest> schedule
    ) {
        Veterinarian veterinarian = getCurrentVeterinarian();
        if (schedule == null || schedule.isEmpty()) {
            throw new BusinessRuleException("Schedule cannot be empty");
        }

        Map<DayOfWeek, UpdateAvailabilityRequest> requestedDays = new EnumMap<>(DayOfWeek.class);
        for (UpdateAvailabilityRequest day : schedule) {
            if (day.getDayOfWeek() == null) {
                throw new BusinessRuleException("Day of week is required");
            }
            if (requestedDays.put(day.getDayOfWeek(), day) != null) {
                throw new BusinessRuleException("A day of the week may only appear once in the schedule");
            }
            validateDaySchedule(day);
        }

        for (UpdateAvailabilityRequest day : requestedDays.values()) {
            VeterinarianAvailability availability = availabilityRepository
                    .findByVeterinarianIdAndDayOfWeek(veterinarian.getId(), day.getDayOfWeek())
                    .orElseGet(() -> VeterinarianAvailability.builder()
                            .veterinarian(veterinarian)
                            .dayOfWeek(day.getDayOfWeek())
                            .build());
            availability.setStartTime(day.getStartTime());
            availability.setEndTime(day.getEndTime());
            availability.setSlotDurationMinutes(day.getSlotDurationMinutes());
            availability.setIsAvailable(Boolean.TRUE.equals(day.getIsAvailable()));
            availability.setIsConfigured(true);
            availabilityRepository.save(availability);
        }

        return toWeeklySchedule(veterinarian.getId());
    }

    private void validateDaySchedule(UpdateAvailabilityRequest day) {
        if (Boolean.TRUE.equals(day.getIsAvailable())) {
            if (day.getStartTime() == null || day.getEndTime() == null) {
                throw new BusinessRuleException("Start time and end time are required for " + day.getDayOfWeek());
            }
            if (!day.getEndTime().isAfter(day.getStartTime())) {
                throw new BusinessRuleException("Start time must be before end time for " + day.getDayOfWeek());
            }
            if (day.getSlotDurationMinutes() == null) {
                throw new BusinessRuleException("Slot duration is required for " + day.getDayOfWeek());
            }
            int slotDuration = day.getSlotDurationMinutes();
            if (slotDuration < 10 || slotDuration > 180) {
                throw new BusinessRuleException("Slot duration must be between 10 and 180 minutes for " + day.getDayOfWeek());
            }
        } else {
            if (day.getStartTime() != null && day.getEndTime() != null && !day.getEndTime().isAfter(day.getStartTime())) {
                throw new BusinessRuleException("Start time must be before end time for " + day.getDayOfWeek());
            }
            if (day.getSlotDurationMinutes() != null) {
                int slotDuration = day.getSlotDurationMinutes();
                if (slotDuration < 10 || slotDuration > 180) {
                    throw new BusinessRuleException("Slot duration must be between 10 and 180 minutes for " + day.getDayOfWeek());
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public VeterinarianSlotsResponse getAvailableSlots(Long veterinarianId, LocalDate date) {
        Veterinarian veterinarian = veterinarianRepository.findById(veterinarianId)
                .orElseThrow(() -> new ResourceNotFoundException("Veterinarian not found with id: " + veterinarianId));
        var schedule = availabilityRepository.findByVeterinarianIdAndDayOfWeek(
                veterinarianId,
                date.getDayOfWeek()
        );

        boolean isConfigured = schedule.isPresent() && Boolean.TRUE.equals(schedule.get().getIsConfigured());

        if (!isConfigured
                || !Boolean.TRUE.equals(veterinarian.getIsActive())
                || veterinarian.getVerificationStatus() != VeterinarianVerificationStatus.APPROVED
                || !Boolean.TRUE.equals(schedule.get().getIsAvailable())) {
            return VeterinarianSlotsResponse.builder()
                    .veterinarianId(veterinarianId)
                    .date(date)
                    .isConfigured(isConfigured)
                    .slotDurationMinutes(schedule.map(VeterinarianAvailability::getSlotDurationMinutes).orElse(null))
                    .slots(List.of())
                    .build();
        }

        VeterinarianAvailability availability = schedule.get();
        if (availability.getSlotDurationMinutes() == null || availability.getSlotDurationMinutes() <= 0) {
            throw new BusinessRuleException("Veterinarian schedule has no valid slot duration configured");
        }

        int duration = availability.getSlotDurationMinutes();
        List<Appointment> appointments =
                appointmentRepository.findByVeterinarianIdAndAppointmentDate(veterinarianId, date);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<VeterinarianTimeSlotResponse> slots = new ArrayList<>();

        for (LocalTime start = availability.getStartTime();
             !start.plusMinutes(duration).isAfter(availability.getEndTime());
             start = start.plusMinutes(duration)) {
            LocalTime slotStart = start;
            LocalTime end = slotStart.plusMinutes(duration);
            boolean notInPast = date.isAfter(today)
                    || (date.equals(today) && slotStart.isAfter(now));
            boolean isAvailable = notInPast && appointments.stream().noneMatch(appointment ->
                    appointment.getStatus() != AppointmentStatus.CANCELLED
                            && appointment.getStatus() != AppointmentStatus.NO_SHOW
                            && appointment.getStartTime().isBefore(end)
                            && appointment.getEndTime().isAfter(slotStart)
            );
            slots.add(VeterinarianTimeSlotResponse.builder()
                    .startTime(slotStart)
                    .endTime(end)
                    .available(isAvailable)
                    .build());
        }

        return VeterinarianSlotsResponse.builder()
                .veterinarianId(veterinarianId)
                .date(date)
                .isConfigured(true)
                .slotDurationMinutes(duration)
                .slots(slots)
                .build();
    }

    private Veterinarian getCurrentVeterinarian() {
        return veterinarianRepository.findByUserId(currentUserService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Veterinarian profile not found for current user"));
    }

    private List<VeterinarianAvailabilityDto> toWeeklySchedule(Long veterinarianId) {
        Map<DayOfWeek, VeterinarianAvailability> stored = new EnumMap<>(DayOfWeek.class);
        availabilityRepository.findByVeterinarianId(veterinarianId)
                .forEach(day -> stored.put(day.getDayOfWeek(), day));

        return List.of(DayOfWeek.values()).stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .map(day -> {
                    VeterinarianAvailability availability = stored.get(day);
                    boolean isConfigured = availability != null && Boolean.TRUE.equals(availability.getIsConfigured());
                    return VeterinarianAvailabilityDto.builder()
                            .id(availability == null ? null : availability.getId())
                            .veterinarianId(veterinarianId)
                            .dayOfWeek(day)
                            .startTime(isConfigured ? availability.getStartTime() : null)
                            .endTime(isConfigured ? availability.getEndTime() : null)
                            .slotDurationMinutes(isConfigured ? availability.getSlotDurationMinutes() : null)
                            .isAvailable(isConfigured && Boolean.TRUE.equals(availability.getIsAvailable()))
                            .isConfigured(isConfigured)
                            .build();
                })
                .toList();
    }
}
