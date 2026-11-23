package com.example.PawConnect.controller;

import com.example.PawConnect.config.RequestIdFilter;
import com.example.PawConnect.dto.request.booking.CancelBookingRequest;
import com.example.PawConnect.dto.request.booking.CreateAppointmentRequest;
import com.example.PawConnect.dto.request.booking.CreateServiceBookingRequest;
import com.example.PawConnect.dto.request.professional.UpdateAvailabilityRequest;
import com.example.PawConnect.dto.request.professional.UpdateWeeklyAvailabilityRequest;
import com.example.PawConnect.model.*;
import com.example.PawConnect.repository.*;
import com.example.PawConnect.security.JwtService;
import com.example.PawConnect.service.impl.ScheduledReminderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class VeterinaryAndBookingRulesIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private VeterinarianRepository veterinarianRepository;

    @Autowired
    private VeterinarianAvailabilityRepository veterinarianAvailabilityRepository;

    @Autowired
    private ServiceProviderRepository serviceProviderRepository;

    @Autowired
    private ServiceProviderAvailabilityRepository serviceProviderAvailabilityRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ServiceBookingRepository serviceBookingRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ScheduledReminderService scheduledReminderService;

    @Autowired
    private JwtService jwtService;

    private User vetUser;
    private Veterinarian vet;
    private String vetToken;

    private User customerUser;
    private String customerToken;

    private User otherCustomerUser;
    private String otherCustomerToken;

    private Pet customerDog;
    private Pet otherDog;

    private ServiceProvider provider;
    private Service service;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter, springSecurityFilterChain)
                .build();

        cleanupDatabase();

        Role vetRole = getOrCreateRole("VETERINARIAN", "Veterinarian");
        Role customerRole = getOrCreateRole("PET_OWNER", "Pet Owner");
        Role providerRole = getOrCreateRole("SERVICE_PROVIDER", "Service Provider");

        vetUser = createUser("vet_mary@test.com", "Mary", "Vet", "+1444111222");
        assignRole(vetUser, vetRole);
        vetToken = jwtService.generateAccessToken(vetUser, List.of("ROLE_VETERINARIAN"));

        vet = veterinarianRepository.save(Veterinarian.builder()
                .user(vetUser)
                .specialization("General Medicine")
                .licenseNumber("VET-LIC-2001")
                .clinicName("Mary Pet Clinic")
                .consultationFee(BigDecimal.valueOf(180.00))
                .verificationStatus(VeterinarianVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        for (DayOfWeek day : DayOfWeek.values()) {
            veterinarianAvailabilityRepository.save(VeterinarianAvailability.builder()
                    .veterinarian(vet)
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .slotDurationMinutes(30)
                    .isAvailable(true)
                    .isConfigured(true)
                    .build());
        }

        customerUser = createUser("alice_test@test.com", "Alice", "Smith", "+1555111222");
        assignRole(customerUser, customerRole);
        customerToken = jwtService.generateAccessToken(customerUser, List.of("ROLE_PET_OWNER", "ROLE_CUSTOMER"));

        otherCustomerUser = createUser("bob_test@test.com", "Bob", "Jones", "+1666111222");
        assignRole(otherCustomerUser, customerRole);
        otherCustomerToken = jwtService.generateAccessToken(otherCustomerUser, List.of("ROLE_PET_OWNER", "ROLE_CUSTOMER"));

        customerDog = petRepository.save(Pet.builder()
                .owner(customerUser)
                .name("Bella")
                .species("Dog")
                .gender(PetGender.FEMALE)
                .status(PetStatus.ACTIVE)
                .build());

        otherDog = petRepository.save(Pet.builder()
                .owner(otherCustomerUser)
                .name("Rocky")
                .species("Dog")
                .gender(PetGender.MALE)
                .status(PetStatus.ACTIVE)
                .build());

        User providerUser = createUser("sam_provider@test.com", "Sam", "Provider", "+1777111222");
        assignRole(providerUser, providerRole);

        provider = serviceProviderRepository.save(ServiceProvider.builder()
                .user(providerUser)
                .providerType(ProviderType.GROOMER)
                .businessName("Sam's Grooming")
                .phone("+1777111222")
                .verificationStatus(ServiceProviderVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        // Working schedule for provider: Monday to Friday 9:00 - 17:00, 30 min intervals
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            serviceProviderAvailabilityRepository.save(ServiceProviderAvailability.builder()
                    .provider(provider)
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .slotDurationMinutes(30)
                    .isAvailable(true)
                    .isConfigured(true)
                    .build());
        }
        // Saturday is explicitly unavailable
        serviceProviderAvailabilityRepository.save(ServiceProviderAvailability.builder()
                .provider(provider)
                .dayOfWeek(DayOfWeek.SATURDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30)
                .isAvailable(false)
                .isConfigured(true)
                .build());
        // Sunday is omitted (unconfigured)

        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name("Grooming Cat")
                .serviceType(ServiceType.GROOMING)
                .description("Cat grooming")
                .isActive(true)
                .build());

        service = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Full Grooming")
                .price(BigDecimal.valueOf(250.00))
                .durationMinutes(60)
                .isActive(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        serviceBookingRepository.deleteAll();
        paymentRepository.deleteAll();
        veterinarianAvailabilityRepository.deleteAll();
        serviceProviderAvailabilityRepository.deleteAll();
        serviceRepository.deleteAll();
        serviceCategoryRepository.deleteAll();
        veterinarianRepository.deleteAll();
        serviceProviderRepository.deleteAll();
        petRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Role getOrCreateRole(String code, String name) {
        return roleRepository.findByRoleCode(code).orElseGet(() ->
                roleRepository.save(Role.builder()
                        .roleCode(code)
                        .roleName(name)
                        .description(name)
                        .build()));
    }

    private User createUser(String email, String first, String last, String phone) {
        return userRepository.save(User.builder()
                .firstName(first)
                .lastName(last)
                .email(email)
                .phone(phone)
                .passwordHash("test_pass")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void assignRole(User user, Role role) {
        userRoleRepository.save(UserRole.builder().user(user).role(role).build());
    }

    // =========================================================================
    // 1. Task 6 – Veterinary Endpoint Route Aliases Tests
    // =========================================================================

    @Test
    void testVeterinarianAvailabilityCanonicalRoutes() throws Exception {
        // Test canonical route: /api/veterinarians/availability
        mockMvc.perform(get("/api/veterinarians/availability")
                        .header("Authorization", "Bearer " + vetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(7)));

        // Test existing frontend alias: /api/vet/availability
        mockMvc.perform(get("/api/vet/availability")
                        .header("Authorization", "Bearer " + vetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(7)));

        // Test PUT on /api/veterinarians/availability
        UpdateWeeklyAvailabilityRequest updateReq = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.WEDNESDAY)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(16, 0))
                        .slotDurationMinutes(30)
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/veterinarians/availability")
                        .header("Authorization", "Bearer " + vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    // =========================================================================
    // 2. Task 7 – Veterinary Appointment Payment Expiry Tests
    // =========================================================================

    @Test
    void testAppointmentPaymentExpiryAssignedAndLifecycle() throws Exception {
        LocalDate date = LocalDate.now().plusDays(3);
        while (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }

        CreateAppointmentRequest req = CreateAppointmentRequest.builder()
                .veterinarianId(vet.getId())
                .petId(customerDog.getId())
                .appointmentDate(date)
                .startTime(LocalTime.of(10, 0))
                .reason("Vaccination checkup")
                .build();

        String res = mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn().getResponse().getContentAsString();

        Long appointmentId = objectMapper.readTree(res).path("data").path("id").asLong();

        Appointment savedApt = appointmentRepository.findById(appointmentId).orElse(null);
        assertNotNull(savedApt);
        assertNotNull(savedApt.getPaymentExpiresAt(), "Paid appointment must have paymentExpiresAt populated");
        assertTrue(savedApt.getPaymentExpiresAt().isAfter(Instant.now()), "Expiry must be in the future");

        // Manually simulate expiration: set expiry into the past
        savedApt.setPaymentExpiresAt(Instant.now().minus(Duration.ofMinutes(5)));
        appointmentRepository.save(savedApt);

        // Run automated expiry sweep
        scheduledReminderService.expireOverduePaymentsSweep();

        Appointment expiredApt = appointmentRepository.findById(appointmentId).orElse(null);
        assertNotNull(expiredApt);
        assertEquals(AppointmentStatus.CANCELLED, expiredApt.getStatus(), "Overdue unpaid appointment must transition to CANCELLED");

        // Verify the slot is released: another customer can now book 10:00 AM on that date
        CreateAppointmentRequest rebookReq = CreateAppointmentRequest.builder()
                .veterinarianId(vet.getId())
                .petId(otherDog.getId())
                .appointmentDate(date)
                .startTime(LocalTime.of(10, 0))
                .reason("Rebooked released slot")
                .build();

        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rebookReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void testPaidAppointmentNotCancelledByExpirySweep() {
        Appointment apt = appointmentRepository.save(Appointment.builder()
                .appointmentNo("APT-PAID-001")
                .pet(customerDog)
                .veterinarian(vet)
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(11, 0))
                .endTime(LocalTime.of(11, 30))
                .consultationFee(BigDecimal.valueOf(180.00))
                .status(AppointmentStatus.CONFIRMED)
                .paymentExpiresAt(Instant.now().minus(Duration.ofMinutes(10)))
                .build());

        Payment payment = paymentRepository.save(Payment.builder()
                .paymentReference("PAY-TEST-001")
                .user(customerUser)
                .amount(BigDecimal.valueOf(180.00))
                .paymentMethod("RAZORPAY")
                .paymentFor(PaymentFor.APPOINTMENT)
                .referenceId(apt.getId())
                .status(PaymentStatus.PAID)
                .build());

        apt.setPayment(payment);
        appointmentRepository.save(apt);

        scheduledReminderService.expireOverduePaymentsSweep();

        Appointment reloaded = appointmentRepository.findById(apt.getId()).orElse(null);
        assertNotNull(reloaded);
        assertEquals(AppointmentStatus.CONFIRMED, reloaded.getStatus(), "Paid appointment must never be cancelled by expiry sweep");
    }

    // =========================================================================
    // 3. Task 5 – Service Booking Validation & Conflict Tests
    // =========================================================================

    @Test
    void testServiceBookingValidAndEndTimeCalculatedByBackend() throws Exception {
        // Find next Tuesday (provider works on Tuesday)
        LocalDate tuesday = LocalDate.now().plusDays(1);
        while (tuesday.getDayOfWeek() != DayOfWeek.TUESDAY) {
            tuesday = tuesday.plusDays(1);
        }

        CreateServiceBookingRequest req = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(customerDog.getId())
                .bookingDate(tuesday)
                .startTime(LocalTime.of(10, 0))
                .notes("Need careful coat trim")
                .build();

        String res = mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(res).path("data").path("id").asLong();
        ServiceBooking saved = serviceBookingRepository.findById(bookingId).orElse(null);
        assertNotNull(saved);
        // Backend calculated: 10:00 + 60 minutes = 11:00
        assertEquals(LocalTime.of(11, 0), saved.getEndTime());
        assertNotNull(saved.getPaymentExpiresAt(), "Price > 0 requires paymentExpiresAt populated");
    }

    @Test
    void testServiceBookingMissingAvailabilityRejected() throws Exception {
        // Sunday has no availability configured for provider
        LocalDate sunday = LocalDate.now().plusDays(1);
        while (sunday.getDayOfWeek() != DayOfWeek.SUNDAY) {
            sunday = sunday.plusDays(1);
        }

        CreateServiceBookingRequest req = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(customerDog.getId())
                .bookingDate(sunday)
                .startTime(LocalTime.of(10, 0))
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testServiceBookingUnavailableWeekdayRejected() throws Exception {
        // Saturday is configured with isAvailable = false
        LocalDate saturday = LocalDate.now().plusDays(1);
        while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
            saturday = saturday.plusDays(1);
        }

        CreateServiceBookingRequest req = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(customerDog.getId())
                .bookingDate(saturday)
                .startTime(LocalTime.of(10, 0))
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testServiceBookingOutsideHoursOrDurationOverflowRejected() throws Exception {
        LocalDate wednesday = LocalDate.now().plusDays(1);
        while (wednesday.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            wednesday = wednesday.plusDays(1);
        }

        // Before working hours (starts at 8:00, working hours start at 9:00)
        CreateServiceBookingRequest beforeHours = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(customerDog.getId())
                .bookingDate(wednesday)
                .startTime(LocalTime.of(8, 0))
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(beforeHours)))
                .andExpect(status().is4xxClientError());

        // Duration overflow: starts at 16:30, 60-min service ends at 17:30 (working hours end at 17:00)
        CreateServiceBookingRequest overflow = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(customerDog.getId())
                .bookingDate(wednesday)
                .startTime(LocalTime.of(16, 30))
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overflow)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testServiceBookingMisalignedSlotRejected() throws Exception {
        LocalDate wednesday = LocalDate.now().plusDays(1);
        while (wednesday.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            wednesday = wednesday.plusDays(1);
        }

        // Slot interval is 30 mins (9:00, 9:30, 10:00...). Requesting 09:15 should be rejected
        CreateServiceBookingRequest misaligned = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(customerDog.getId())
                .bookingDate(wednesday)
                .startTime(LocalTime.of(9, 15))
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(misaligned)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testServiceBookingInvalidPetOwnershipRejected() throws Exception {
        LocalDate wednesday = LocalDate.now().plusDays(1);
        while (wednesday.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            wednesday = wednesday.plusDays(1);
        }

        // Alice tries to book using Bob's pet (otherDog)
        CreateServiceBookingRequest req = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(otherDog.getId())
                .bookingDate(wednesday)
                .startTime(LocalTime.of(10, 0))
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCancelledBookingReleasesSlot() throws Exception {
        LocalDate thursday = LocalDate.now().plusDays(1);
        while (thursday.getDayOfWeek() != DayOfWeek.THURSDAY) {
            thursday = thursday.plusDays(1);
        }

        CreateServiceBookingRequest req = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(customerDog.getId())
                .bookingDate(thursday)
                .startTime(LocalTime.of(14, 0))
                .build();

        String res = mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(res).path("data").path("id").asLong();

        // Customer cancels the booking
        CancelBookingRequest cancelReq = CancelBookingRequest.builder()
                .reason("Schedule changed")
                .build();

        mockMvc.perform(patch("/api/me/service-bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk());

        // Bob can now book the exact same slot without conflict
        CreateServiceBookingRequest bobReq = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(service.getId())
                .petId(otherDog.getId())
                .bookingDate(thursday)
                .startTime(LocalTime.of(14, 0))
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)));
    }

    // =========================================================================
    // 4. Task 2 & 11 – Veterinarian Availability Management & Validation Tests
    // =========================================================================

    @Test
    void testNewVeterinarianWithoutScheduleReturnsUnconfigured() throws Exception {
        User newVetUser = createUser("dr_new@test.com", "David", "New", "+1999888777");
        Role vetRole = getOrCreateRole("VETERINARIAN", "Veterinarian");
        assignRole(newVetUser, vetRole);
        String newVetToken = jwtService.generateAccessToken(newVetUser, List.of("ROLE_VETERINARIAN"));

        Veterinarian newVet = veterinarianRepository.save(Veterinarian.builder()
                .user(newVetUser)
                .specialization("Feline Care")
                .licenseNumber("VET-LIC-3001")
                .clinicName("Feline Care Clinic")
                .consultationFee(BigDecimal.valueOf(150.00))
                .verificationStatus(VeterinarianVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        // Get availability for new vet: all 7 days should have isConfigured = false
        mockMvc.perform(get("/api/veterinarians/availability")
                        .header("Authorization", "Bearer " + newVetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[0].isConfigured", is(false)));

        // Public slots for new vet should return configured = false and 0 slots
        mockMvc.perform(get("/api/public/veterinarians/" + newVet.getId() + "/availability")
                        .param("date", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.configured", is(false)))
                .andExpect(jsonPath("$.data.slots", hasSize(0)));

        // Attempting to book an appointment with unconfigured vet should be rejected
        CreateAppointmentRequest bookUnconfigured = CreateAppointmentRequest.builder()
                .veterinarianId(newVet.getId())
                .petId(customerDog.getId())
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(10, 0))
                .reason("Health check")
                .build();

        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookUnconfigured)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testSaveAllSevenWeekdaysAndUpdateOnePreservesOthers() throws Exception {
        UpdateWeeklyAvailabilityRequest updateReq = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.TUESDAY)
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(18, 0))
                        .slotDurationMinutes(45)
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/veterinarians/availability")
                        .header("Authorization", "Bearer " + vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        List<VeterinarianAvailability> schedules = veterinarianAvailabilityRepository.findByVeterinarianId(vet.getId());
        assertEquals(7, schedules.size());

        VeterinarianAvailability tuesday = schedules.stream()
                .filter(s -> s.getDayOfWeek() == DayOfWeek.TUESDAY).findFirst().orElseThrow();
        assertEquals(LocalTime.of(10, 0), tuesday.getStartTime());
        assertEquals(LocalTime.of(18, 0), tuesday.getEndTime());
        assertEquals(45, tuesday.getSlotDurationMinutes());
        assertTrue(tuesday.getIsConfigured());

        VeterinarianAvailability monday = schedules.stream()
                .filter(s -> s.getDayOfWeek() == DayOfWeek.MONDAY).findFirst().orElseThrow();
        assertEquals(LocalTime.of(9, 0), monday.getStartTime());
        assertEquals(LocalTime.of(17, 0), monday.getEndTime());
        assertEquals(30, monday.getSlotDurationMinutes());
        assertTrue(monday.getIsConfigured());
    }

    @Test
    void testSaveScheduleWithInactiveDaysHavingNullTimesSucceeds() throws Exception {
        UpdateWeeklyAvailabilityRequest updateReq = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(
                        UpdateAvailabilityRequest.builder()
                                .dayOfWeek(DayOfWeek.MONDAY)
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(17, 0))
                                .slotDurationMinutes(30)
                                .isAvailable(true)
                                .build(),
                        UpdateAvailabilityRequest.builder()
                                .dayOfWeek(DayOfWeek.TUESDAY)
                                .startTime(null)
                                .endTime(null)
                                .slotDurationMinutes(null)
                                .isAvailable(false)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/vet/availability")
                        .header("Authorization", "Bearer " + vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        VeterinarianAvailability tuesday = veterinarianAvailabilityRepository
                .findByVeterinarianIdAndDayOfWeek(vet.getId(), DayOfWeek.TUESDAY)
                .orElseThrow();
        assertFalse(tuesday.getIsAvailable());
        assertTrue(tuesday.getIsConfigured());
        assertNull(tuesday.getStartTime());
        assertNull(tuesday.getEndTime());
        assertNull(tuesday.getSlotDurationMinutes());
    }

    @Test
    void testRejectInvalidWorkingHoursAndDuration() throws Exception {
        // End time before start time
        UpdateWeeklyAvailabilityRequest invalidHours = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .startTime(LocalTime.of(17, 0))
                        .endTime(LocalTime.of(9, 0))
                        .slotDurationMinutes(30)
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/veterinarians/availability")
                        .header("Authorization", "Bearer " + vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidHours)))
                .andExpect(status().is4xxClientError());

        // Invalid duration <= 0
        UpdateWeeklyAvailabilityRequest invalidDuration = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .slotDurationMinutes(0)
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/veterinarians/availability")
                        .header("Authorization", "Bearer " + vetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDuration)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testPreventUnauthorizedScheduleAccess() throws Exception {
        UpdateWeeklyAvailabilityRequest updateReq = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .slotDurationMinutes(30)
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/veterinarians/availability")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testVeterinaryAppointmentOverlappingConflictRejected() throws Exception {
        LocalDate date = LocalDate.now().plusDays(2);
        while (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }

        CreateAppointmentRequest aliceReq = CreateAppointmentRequest.builder()
                .veterinarianId(vet.getId())
                .petId(customerDog.getId())
                .appointmentDate(date)
                .startTime(LocalTime.of(9, 30))
                .reason("Dental cleaning")
                .build();

        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aliceReq)))
                .andExpect(status().isCreated());

        CreateAppointmentRequest bobReq = CreateAppointmentRequest.builder()
                .veterinarianId(vet.getId())
                .petId(otherDog.getId())
                .appointmentDate(date)
                .startTime(LocalTime.of(9, 30))
                .reason("Vaccination")
                .build();

        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void testFreeAppointmentHasNullPaymentExpiresAt() throws Exception {
        User freeVetUser = createUser("dr_free@test.com", "Free", "Doc", "+1888999000");
        Role vetRole = getOrCreateRole("VETERINARIAN", "Veterinarian");
        assignRole(freeVetUser, vetRole);

        Veterinarian freeVet = veterinarianRepository.save(Veterinarian.builder()
                .user(freeVetUser)
                .specialization("Volunteer Care")
                .licenseNumber("VET-FREE-101")
                .clinicName("Community Pet Clinic")
                .consultationFee(BigDecimal.ZERO)
                .verificationStatus(VeterinarianVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        for (DayOfWeek day : DayOfWeek.values()) {
            veterinarianAvailabilityRepository.save(VeterinarianAvailability.builder()
                    .veterinarian(freeVet)
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .slotDurationMinutes(30)
                    .isAvailable(true)
                    .isConfigured(true)
                    .build());
        }

        LocalDate date = LocalDate.now().plusDays(2);
        CreateAppointmentRequest req = CreateAppointmentRequest.builder()
                .veterinarianId(freeVet.getId())
                .petId(customerDog.getId())
                .appointmentDate(date)
                .startTime(LocalTime.of(10, 0))
                .reason("Free checkup")
                .build();

        String res = mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Long aptId = objectMapper.readTree(res).path("data").path("id").asLong();
        Appointment saved = appointmentRepository.findById(aptId).orElseThrow();
        assertNull(saved.getPaymentExpiresAt(), "Free appointment must not have payment expiry date set");
        assertEquals(AppointmentStatus.PENDING, saved.getStatus());

        // Run expiry sweep — appointment without paymentExpiresAt must NOT be cancelled
        scheduledReminderService.expireOverduePaymentsSweep();

        Appointment reloaded = appointmentRepository.findById(aptId).orElseThrow();
        assertEquals(AppointmentStatus.PENDING, reloaded.getStatus(), "Free appointment without expiry date must remain active");
    }

    @Test
    void testPublicVeterinarianDiscoveryAndSlots() throws Exception {
        mockMvc.perform(get("/api/public/veterinarians"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", not(empty())))
                .andExpect(jsonPath("$.data.content[0].consultationFee", notNullValue()));

        LocalDate date = LocalDate.now().plusDays(2);
        while (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }

        mockMvc.perform(get("/api/public/veterinarians/" + vet.getId() + "/availability")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.configured", is(true)))
                .andExpect(jsonPath("$.data.slots", not(empty())));
    }
}
