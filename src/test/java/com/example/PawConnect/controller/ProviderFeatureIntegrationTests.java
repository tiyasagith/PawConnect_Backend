package com.example.PawConnect.controller;

import com.example.PawConnect.config.RequestIdFilter;
import com.example.PawConnect.dto.request.professional.UpdateAvailabilityRequest;
import com.example.PawConnect.dto.request.professional.UpdateWeeklyAvailabilityRequest;
import com.example.PawConnect.dto.request.service.CreateServiceRequest;
import com.example.PawConnect.dto.request.service.UpdateServiceRequest;
import com.example.PawConnect.model.*;
import com.example.PawConnect.repository.*;
import com.example.PawConnect.dto.request.booking.CreateServiceBookingRequest;
import com.example.PawConnect.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:provider_feature_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class ProviderFeatureIntegrationTests {

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
    private PetRepository petRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtService jwtService;

    private User providerUser;
    private ServiceProvider provider;
    private String providerToken;

    private User otherProviderUser;
    private ServiceProvider otherProvider;
    private String otherProviderToken;

    private User customerUser;
    private String customerToken;

    private ServiceCategory groomingCategory;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter, springSecurityFilterChain)
                .build();

        cleanupDatabase();

        Role providerRole = getOrCreateRole("SERVICE_PROVIDER", "Service Provider");
        Role customerRole = getOrCreateRole("PET_OWNER", "Pet Owner");

        providerUser = createUser("provider_test@test.com", "Test", "Provider", "+1111111111");
        assignRole(providerUser, providerRole);
        providerToken = jwtService.generateAccessToken(providerUser, List.of("ROLE_SERVICE_PROVIDER"));

        provider = serviceProviderRepository.save(ServiceProvider.builder()
                .user(providerUser)
                .providerType(ProviderType.GROOMER)
                .businessName("Test Grooming Care")
                .phone("+1111111111")
                .verificationStatus(ServiceProviderVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        otherProviderUser = createUser("other_provider@test.com", "Other", "Provider", "+1222222222");
        assignRole(otherProviderUser, providerRole);
        otherProviderToken = jwtService.generateAccessToken(otherProviderUser, List.of("ROLE_SERVICE_PROVIDER"));

        otherProvider = serviceProviderRepository.save(ServiceProvider.builder()
                .user(otherProviderUser)
                .providerType(ProviderType.GROOMER)
                .businessName("Other Grooming Spa")
                .phone("+1222222222")
                .verificationStatus(ServiceProviderVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        customerUser = createUser("customer_test@test.com", "Jane", "Customer", "+1333333333");
        assignRole(customerUser, customerRole);
        customerToken = jwtService.generateAccessToken(customerUser, List.of("ROLE_PET_OWNER", "ROLE_CUSTOMER"));

        groomingCategory = serviceCategoryRepository.save(ServiceCategory.builder()
                .name("Test Grooming")
                .serviceType(ServiceType.GROOMING)
                .description("Professional test grooming")
                .isActive(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        notificationRepository.deleteAll();
        serviceBookingRepository.deleteAll();
        serviceProviderAvailabilityRepository.deleteAll();
        serviceRepository.deleteAll();
        serviceCategoryRepository.deleteAll();
        petRepository.deleteAll();
        serviceProviderRepository.deleteAll();
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
                .passwordHash("encoded_pass")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void assignRole(User user, Role role) {
        userRoleRepository.save(UserRole.builder().user(user).role(role).build());
    }

    // =========================================================================
    // 1. Provider Availability Tests (Phase 3 Verified)
    // =========================================================================

    @Test
    void testGetNewUnconfiguredProviderReturnsUnconfiguredState() throws Exception {
        // When a new provider has not saved availability, GET returns unconfigured state and empty days
        mockMvc.perform(get("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.configured", is(false)))
                .andExpect(jsonPath("$.data.message", containsString("not been configured")))
                .andExpect(jsonPath("$.data.days", hasSize(0)));

        // Verify that GET did not automatically insert any default records into the database
        List<ServiceProviderAvailability> rows = serviceProviderAvailabilityRepository.findByProviderId(provider.getId());
        assertTrue(rows.isEmpty(), "GET must not seed default working hours in the database");
    }

    @Test
    void testProviderCanExplicitlySaveAllSevenWeekdaysAndPersist() throws Exception {
        List<UpdateAvailabilityRequest> schedule = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            schedule.add(UpdateAvailabilityRequest.builder()
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .slotDurationMinutes(45)
                    .isAvailable(day != DayOfWeek.SUNDAY)
                    .build());
        }

        UpdateWeeklyAvailabilityRequest req = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(schedule)
                .build();

        mockMvc.perform(put("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.configured", is(true)))
                .andExpect(jsonPath("$.data.days", hasSize(7)))
                .andExpect(jsonPath("$.data.days[0].slotDurationMinutes", is(45)))
                .andExpect(jsonPath("$.data.days[0].isConfigured", is(true)));

        // Subsequent GET returns configured schedule
        mockMvc.perform(get("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.configured", is(true)))
                .andExpect(jsonPath("$.data.days", hasSize(7)))
                .andExpect(jsonPath("$.data.days[0].dayOfWeek", is("MONDAY")))
                .andExpect(jsonPath("$.data.days[6].dayOfWeek", is("SUNDAY")))
                .andExpect(jsonPath("$.data.days[6].isAvailable", is(false)));
    }

    @Test
    void testIndividualWeekdayUpdatePreservesOtherWeekdays() throws Exception {
        // First, save a weekly schedule
        List<UpdateAvailabilityRequest> initial = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            initial.add(UpdateAvailabilityRequest.builder()
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .slotDurationMinutes(30)
                    .isAvailable(true)
                    .build());
        }
        mockMvc.perform(put("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateWeeklyAvailabilityRequest.builder().schedule(initial).build())))
                .andExpect(status().isOk());

        // Update only Wednesday via single-day endpoint
        UpdateAvailabilityRequest wednesdayUpdate = UpdateAvailabilityRequest.builder()
                .dayOfWeek(DayOfWeek.WEDNESDAY)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(18, 0))
                .slotDurationMinutes(60)
                .isAvailable(true)
                .build();

        mockMvc.perform(put("/api/provider/availability/WEDNESDAY")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wednesdayUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.configured", is(true)))
                .andExpect(jsonPath("$.data.days", hasSize(7)));

        // Verify in DB: Wednesday has 10:00-18:00 (60 min) and Monday still has 09:00-17:00 (30 min)
        ServiceProviderAvailability wed = serviceProviderAvailabilityRepository
                .findByProviderIdAndDayOfWeek(provider.getId(), DayOfWeek.WEDNESDAY).orElseThrow();
        assertEquals(LocalTime.of(10, 0), wed.getStartTime());
        assertEquals(LocalTime.of(18, 0), wed.getEndTime());
        assertEquals(60, wed.getSlotDurationMinutes());

        ServiceProviderAvailability mon = serviceProviderAvailabilityRepository
                .findByProviderIdAndDayOfWeek(provider.getId(), DayOfWeek.MONDAY).orElseThrow();
        assertEquals(LocalTime.of(9, 0), mon.getStartTime());
        assertEquals(LocalTime.of(17, 0), mon.getEndTime());
        assertEquals(30, mon.getSlotDurationMinutes());
    }

    @Test
    void testRejectInvalidAvailabilityTimesAndInterval() throws Exception {
        UpdateWeeklyAvailabilityRequest invalidTimes = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .startTime(LocalTime.of(18, 0))
                        .endTime(LocalTime.of(9, 0))
                        .slotDurationMinutes(30)
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTimes)))
                .andExpect(status().is4xxClientError());

        UpdateWeeklyAvailabilityRequest invalidInterval = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .slotDurationMinutes(5) // Less than minimum 10 min
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidInterval)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testRejectDuplicateWeekdayInAvailabilityRequest() throws Exception {
        UpdateWeeklyAvailabilityRequest req = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(
                        UpdateAvailabilityRequest.builder()
                                .dayOfWeek(DayOfWeek.TUESDAY)
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(12, 0))
                                .slotDurationMinutes(30)
                                .isAvailable(true)
                                .build(),
                        UpdateAvailabilityRequest.builder()
                                .dayOfWeek(DayOfWeek.TUESDAY)
                                .startTime(LocalTime.of(13, 0))
                                .endTime(LocalTime.of(17, 0))
                                .slotDurationMinutes(30)
                                .isAvailable(true)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testRejectUnauthorizedAvailabilityAccess() throws Exception {
        mockMvc.perform(get("/api/provider/availability")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        UpdateWeeklyAvailabilityRequest dummy = UpdateWeeklyAvailabilityRequest.builder()
                .schedule(List.of(UpdateAvailabilityRequest.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .slotDurationMinutes(30)
                        .isAvailable(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/provider/availability")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dummy)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUnconfiguredProviderReturnsNoPublicSlots() throws Exception {
        Service srv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Standard Bath")
                .price(BigDecimal.valueOf(100.00))
                .durationMinutes(30)
                .isActive(true)
                .build());

        LocalDate futureDate = LocalDate.now().plusDays(2);
        // Provider has not configured availability -> public discovery must return empty slots
        mockMvc.perform(get("/api/public/service-providers/" + provider.getId() + "/availability")
                        .param("date", futureDate.toString())
                        .param("serviceId", srv.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.slots", hasSize(0)));
    }

    @Test
    void testPreviouslyStoredUnconfiguredRecordsHandledSafely() throws Exception {
        // Simulate legacy unconfigured record in DB with isConfigured = false
        serviceProviderAvailabilityRepository.save(ServiceProviderAvailability.builder()
                .provider(provider)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30)
                .isAvailable(true)
                .isConfigured(false)
                .build());

        // GET must still report unconfigured state because isConfigured is false
        mockMvc.perform(get("/api/provider/availability")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured", is(false)))
                .andExpect(jsonPath("$.data.days", hasSize(0)));
    }

    // =========================================================================
    // 2. Provider Service CRUD Tests
    // =========================================================================

    @Test
    void serviceGalleryPersistsUpdatesAndIsVisibleToCustomers() throws Exception {
        CreateServiceRequest create = CreateServiceRequest.builder()
                .name("Photo package")
                .price(BigDecimal.valueOf(500))
                .durationMinutes(45)
                .imageUrls(List.of("/uploads/general/one.jpg", "/uploads/general/two.jpg"))
                .build();

        String response = mockMvc.perform(post("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imageUrl", is("/uploads/general/one.jpg")))
                .andExpect(jsonPath("$.data.imageUrls", contains("/uploads/general/one.jpg", "/uploads/general/two.jpg")))
                .andReturn().getResponse().getContentAsString();
        long serviceId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(get("/api/public/service-providers/" + provider.getId() + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].imageUrls", contains("/uploads/general/one.jpg", "/uploads/general/two.jpg")));

        UpdateServiceRequest update = UpdateServiceRequest.builder()
                .name("Photo package")
                .price(BigDecimal.valueOf(500))
                .durationMinutes(45)
                .imageUrls(List.of("/uploads/general/two.jpg"))
                .build();
        mockMvc.perform(put("/api/provider/services/" + serviceId)
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl", is("/uploads/general/two.jpg")))
                .andExpect(jsonPath("$.data.imageUrls", contains("/uploads/general/two.jpg")));

        update.setImageUrls(List.of());
        mockMvc.perform(put("/api/provider/services/" + serviceId)
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrls", empty()));

        Service legacy = serviceRepository.save(Service.builder().provider(provider)
                .name("Legacy photo").price(BigDecimal.TEN).durationMinutes(30)
                .imageUrl("/uploads/general/legacy.jpg").isActive(true).build());
        mockMvc.perform(get("/api/provider/services/" + legacy.getId())
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrls", contains("/uploads/general/legacy.jpg")));
    }

    @Test
    void testCreateGetUpdateAndDeactivateService() throws Exception {
        CreateServiceRequest createReq = CreateServiceRequest.builder()
                .categoryId(groomingCategory.getId())
                .name("Bath & Blow Dry")
                .description("Includes organic shampoo and dry")
                .price(BigDecimal.valueOf(450.00))
                .durationMinutes(45)
                .isActive(true)
                .build();

        String res = mockMvc.perform(post("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Bath & Blow Dry")))
                .andExpect(jsonPath("$.data.price", is(450.0)))
                .andExpect(jsonPath("$.data.durationMinutes", is(45)))
                .andReturn().getResponse().getContentAsString();

        Long serviceId = objectMapper.readTree(res).path("data").path("id").asLong();

        mockMvc.perform(get("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(serviceId.intValue())));

        UpdateServiceRequest updateReq = UpdateServiceRequest.builder()
                .categoryId(groomingCategory.getId())
                .name("Luxury Bath & Blow Dry")
                .description("Updated description")
                .price(BigDecimal.valueOf(550.00))
                .durationMinutes(60)
                .isActive(true)
                .build();

        mockMvc.perform(put("/api/provider/services/" + serviceId)
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Luxury Bath & Blow Dry")))
                .andExpect(jsonPath("$.data.price", is(550.0)));

        mockMvc.perform(delete("/api/provider/services/" + serviceId)
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void testServiceWithBookingsIsSoftDeactivatedOnDelete() throws Exception {
        Service srv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Nail Clipping")
                .price(BigDecimal.valueOf(150.00))
                .durationMinutes(30)
                .isActive(true)
                .build());

        Pet pet = petRepository.save(Pet.builder()
                .owner(customerUser)
                .name("Charlie")
                .species("Dog")
                .gender(PetGender.MALE)
                .status(PetStatus.ACTIVE)
                .build());

        serviceBookingRepository.save(ServiceBooking.builder()
                .bookingNo("SB-HIST-101")
                .pet(pet)
                .provider(provider)
                .service(srv)
                .bookingDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .price(BigDecimal.valueOf(150.00))
                .status(BookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(delete("/api/provider/services/" + srv.getId())
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        Service archived = serviceRepository.findById(srv.getId()).orElse(null);
        assertNotNull(archived);
        assertFalse(archived.getIsActive(), "Service with historical bookings should be deactivated, not deleted");
    }

    @Test
    void testPreventProviderFromModifyingAnotherProvidersService() throws Exception {
        Service srv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Evan's Service")
                .price(BigDecimal.valueOf(200.00))
                .durationMinutes(30)
                .isActive(true)
                .build());

        UpdateServiceRequest updateReq = UpdateServiceRequest.builder()
                .categoryId(groomingCategory.getId())
                .name("Tampered Name")
                .price(BigDecimal.valueOf(300.00))
                .durationMinutes(30)
                .isActive(true)
                .build();

        mockMvc.perform(put("/api/provider/services/" + srv.getId())
                        .header("Authorization", "Bearer " + otherProviderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/provider/services/" + srv.getId())
                        .header("Authorization", "Bearer " + otherProviderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testProviderGetCategoriesReturnsActiveCategories() throws Exception {
        mockMvc.perform(get("/api/provider/services/categories")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].id", is(groomingCategory.getId().intValue())))
                .andExpect(jsonPath("$.data[0].name", is(groomingCategory.getName())))
                .andExpect(jsonPath("$.data[0].isActive", is(true)));
    }

    @Test
    void testPublicServiceCategoriesReturnsActiveCategories() throws Exception {
        mockMvc.perform(get("/api/public/service-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].name", is(groomingCategory.getName())));
    }

    @Test
    void testCreateServiceUsesProviderTypeWithoutCategory() throws Exception {
        // Missing category ID
        CreateServiceRequest missingCatReq = CreateServiceRequest.builder()
                .categoryId(null)
                .name("Bath")
                .price(BigDecimal.valueOf(100.0))
                .durationMinutes(30)
                .build();

        mockMvc.perform(post("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingCatReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serviceType", is("GROOMING")));

        // Non-existent category ID (999999L)
        CreateServiceRequest invalidCatReq = CreateServiceRequest.builder()
                .categoryId(999999L)
                .name("Bath")
                .price(BigDecimal.valueOf(100.0))
                .durationMinutes(30)
                .build();

        mockMvc.perform(post("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCatReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serviceType", is("GROOMING")));
    }

    @Test
    void testCreateServiceRejectsInvalidPriceAndDuration() throws Exception {
        // Negative price
        CreateServiceRequest negativePriceReq = CreateServiceRequest.builder()
                .categoryId(groomingCategory.getId())
                .name("Bath")
                .price(BigDecimal.valueOf(-10.0))
                .durationMinutes(30)
                .build();

        mockMvc.perform(post("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativePriceReq)))
                .andExpect(status().is4xxClientError());

        // Zero duration
        CreateServiceRequest zeroDurationReq = CreateServiceRequest.builder()
                .categoryId(groomingCategory.getId())
                .name("Bath")
                .price(BigDecimal.valueOf(100.0))
                .durationMinutes(0)
                .build();

        mockMvc.perform(post("/api/provider/services")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroDurationReq)))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 3. Public Provider Services & Availability Slot Generation Tests
    // =========================================================================

    @Test
    void testPublicServicesReturnsOnlyActive() throws Exception {
        serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Active Wash")
                .price(BigDecimal.valueOf(100.00))
                .durationMinutes(30)
                .isActive(true)
                .build());

        serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Inactive Special")
                .price(BigDecimal.valueOf(80.00))
                .durationMinutes(30)
                .isActive(false)
                .build());

        mockMvc.perform(get("/api/public/service-providers/" + provider.getId() + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Active Wash")))
                .andExpect(jsonPath("$.data[0].isActive", is(true)));
    }

    @Test
    void testPublicAvailabilityGeneratesSlotsAndExcludesConflicts() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(5);

        serviceProviderAvailabilityRepository.save(ServiceProviderAvailability.builder()
                .provider(provider)
                .dayOfWeek(targetDate.getDayOfWeek())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(30)
                .isAvailable(true)
                .isConfigured(true)
                .build());

        Service srv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Full Groom")
                .price(BigDecimal.valueOf(300.00))
                .durationMinutes(60)
                .isActive(true)
                .build());

        Pet pet = petRepository.save(Pet.builder()
                .owner(customerUser)
                .name("Buddy")
                .species("Dog")
                .gender(PetGender.MALE)
                .status(PetStatus.ACTIVE)
                .build());

        serviceBookingRepository.save(ServiceBooking.builder()
                .bookingNo("SB-TEST-CONF-1")
                .pet(pet)
                .provider(provider)
                .service(srv)
                .bookingDate(targetDate)
                .startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 30))
                .price(BigDecimal.valueOf(300.00))
                .status(BookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(get("/api/public/service-providers/" + provider.getId() + "/availability")
                        .param("date", targetDate.toString())
                        .param("serviceId", srv.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.providerId", is(provider.getId().intValue())))
                .andExpect(jsonPath("$.data.serviceDurationMinutes", is(60)))
                .andExpect(jsonPath("$.data.slotDurationMinutes", is(30)))
                .andExpect(jsonPath("$.data.slots", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.slots[0].startTime", is("09:00:00")))
                .andExpect(jsonPath("$.data.slots[0].available", is(false))) // Overlaps with 9:30-10:30 booking
                .andExpect(jsonPath("$.data.slots[1].startTime", is("09:30:00")))
                .andExpect(jsonPath("$.data.slots[1].available", is(false)))
                .andExpect(jsonPath("$.data.slots[3].startTime", is("10:30:00")))
                .andExpect(jsonPath("$.data.slots[3].available", is(true)));
    }

    @Test
    void getProviderAvailableSlots_serviceWithoutValidDuration_returnsBadRequest() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        serviceProviderAvailabilityRepository.save(ServiceProviderAvailability.builder()
                .provider(provider)
                .dayOfWeek(targetDate.getDayOfWeek())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30)
                .isAvailable(true)
                .isConfigured(true)
                .build());

        Service srv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Duration Missing Service")
                .price(BigDecimal.valueOf(100.00))
                .durationMinutes(0)
                .isActive(true)
                .build());

        mockMvc.perform(get("/api/public/service-providers/" + provider.getId() + "/availability")
                        .param("date", targetDate.toString())
                        .param("serviceId", srv.getId().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Selected service has no valid duration configured")));
    }

    @Test
    void getProviderAvailableSlots_providerUnconfigured_returnsEmptySlots() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        Service srv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Bath & Groom")
                .price(BigDecimal.valueOf(150.00))
                .durationMinutes(30)
                .isActive(true)
                .build());

        mockMvc.perform(get("/api/public/service-providers/" + provider.getId() + "/availability")
                        .param("date", targetDate.toString())
                        .param("serviceId", srv.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.slots", hasSize(0)));
    }

    @Test
    void createServiceBooking_serviceWithoutValidDuration_returnsBadRequest() throws Exception {
        LocalDate bookingDate = LocalDate.now().plusDays(3);
        serviceProviderAvailabilityRepository.save(ServiceProviderAvailability.builder()
                .provider(provider)
                .dayOfWeek(bookingDate.getDayOfWeek())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30)
                .isAvailable(true)
                .isConfigured(true)
                .build());

        Service srv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("No Duration Service")
                .price(BigDecimal.valueOf(200.00))
                .durationMinutes(0)
                .isActive(true)
                .build());

        Pet pet = petRepository.save(Pet.builder()
                .owner(customerUser)
                .name("Fluffy")
                .species("Cat")
                .gender(PetGender.FEMALE)
                .status(PetStatus.ACTIVE)
                .build());

        CreateServiceBookingRequest request = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(srv.getId())
                .petId(pet.getId())
                .bookingDate(bookingDate)
                .startTime(LocalTime.of(10, 0))
                .notes("Check duration")
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Selected service has no valid duration configured")));
    }

    @Test
    void getProviderServices_returnsActualPersistedServicesWithServiceType() throws Exception {
        ServiceCategory trainingCategory = serviceCategoryRepository.save(ServiceCategory.builder()
                .name("Canine Coaching")
                .serviceType(ServiceType.TRAINING)
                .description("Training test category")
                .isActive(true)
                .build());

        serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Premium Dog Spa")
                .price(BigDecimal.valueOf(500.00))
                .durationMinutes(45)
                .isActive(true)
                .build());

        serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Canine Agility")
                .price(BigDecimal.valueOf(800.00))
                .durationMinutes(60)
                .isActive(true)
                .build());

        mockMvc.perform(get("/api/public/service-providers/" + provider.getId() + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[?(@.name == 'Premium Dog Spa')].serviceType", contains("GROOMING")))
                .andExpect(jsonPath("$.data[?(@.name == 'Canine Agility')].serviceType", contains("GROOMING")))
                .andExpect(jsonPath("$.data[?(@.name == 'Premium Dog Spa')].durationMinutes", contains(45)));
    }

    @Test
    void createServiceBooking_groomingBookingSuccessAndConflictDetection() throws Exception {
        LocalDate bookingDate = LocalDate.now().plusDays(4);
        serviceProviderAvailabilityRepository.save(ServiceProviderAvailability.builder()
                .provider(provider)
                .dayOfWeek(bookingDate.getDayOfWeek())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30)
                .isAvailable(true)
                .isConfigured(true)
                .build());

        Service groomingSrv = serviceRepository.save(Service.builder()
                .provider(provider)
                .name("Deep Coat Conditioning")
                .price(BigDecimal.valueOf(350.00))
                .durationMinutes(60)
                .isActive(true)
                .build());

        Pet pet = petRepository.save(Pet.builder()
                .owner(customerUser)
                .name("Bella")
                .species("Dog")
                .gender(PetGender.FEMALE)
                .status(PetStatus.ACTIVE)
                .build());

        CreateServiceBookingRequest bookingReq = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(groomingSrv.getId())
                .petId(pet.getId())
                .bookingDate(bookingDate)
                .startTime(LocalTime.of(11, 0))
                .notes("First grooming visit")
                .build();

        // 1. Initial booking succeeds
        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.serviceName", is("Deep Coat Conditioning")))
                .andExpect(jsonPath("$.data.bookingNo", startsWith("SB-")))
                .andExpect(jsonPath("$.data.startTime", is("11:00:00")))
                .andExpect(jsonPath("$.data.endTime", is("12:00:00")));

        // 2. Conflicting booking during overlapping window (11:30 - 12:30) is rejected
        CreateServiceBookingRequest conflictReq = CreateServiceBookingRequest.builder()
                .providerId(provider.getId())
                .serviceId(groomingSrv.getId())
                .petId(pet.getId())
                .bookingDate(bookingDate)
                .startTime(LocalTime.of(11, 30))
                .notes("Conflicting attempt")
                .build();

        mockMvc.perform(post("/api/me/service-bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already has a booking during the requested time slot")));
    }
}
