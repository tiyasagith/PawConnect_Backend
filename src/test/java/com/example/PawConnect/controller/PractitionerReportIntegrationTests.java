package com.example.PawConnect.controller;

import com.example.PawConnect.config.RequestIdFilter;
import com.example.PawConnect.model.*;
import com.example.PawConnect.repository.*;
import com.example.PawConnect.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class PractitionerReportIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private VeterinarianRepository veterinarianRepository;

    @Autowired
    private ServiceProviderRepository serviceProviderRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ServiceBookingRepository serviceBookingRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String vetToken;
    private String providerToken;
    private String customerToken;

    private Veterinarian testVet;
    private ServiceProvider testProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter, springSecurityFilterChain)
                .build();

        Role adminRole = roleRepository.findByRoleCode("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().roleCode("ADMIN").roleName("Admin").build()));
        Role petOwnerRole = roleRepository.findByRoleCode("PET_OWNER").orElseGet(() ->
                roleRepository.save(Role.builder().roleCode("PET_OWNER").roleName("Pet Owner").build()));
        Role vetRole = roleRepository.findByRoleCode("VETERINARIAN").orElseGet(() ->
                roleRepository.save(Role.builder().roleCode("VETERINARIAN").roleName("Veterinarian").build()));
        Role providerRole = roleRepository.findByRoleCode("SERVICE_PROVIDER").orElseGet(() ->
                roleRepository.save(Role.builder().roleCode("SERVICE_PROVIDER").roleName("Service Provider").build()));

        long timestamp = System.currentTimeMillis();

        // 1. Admin
        User adminUser = userRepository.save(User.builder()
                .firstName("Report")
                .lastName("Admin")
                .email("admin_rep_" + timestamp + "@test.com")
                .phone("+9111" + (timestamp % 100000000))
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .status(UserStatus.ACTIVE)
                .tokenVersion(1)
                .build());
        userRoleRepository.save(UserRole.builder().user(adminUser).role(adminRole).build());
        adminToken = jwtService.generateAccessToken(adminUser, List.of("ROLE_ADMIN"));

        // 2. Customer
        User customerUser = userRepository.save(User.builder()
                .firstName("Pet")
                .lastName("Parent")
                .email("cust_rep_" + timestamp + "@test.com")
                .phone("+9122" + (timestamp % 100000000))
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .status(UserStatus.ACTIVE)
                .tokenVersion(1)
                .build());
        userRoleRepository.save(UserRole.builder().user(customerUser).role(petOwnerRole).build());
        customerToken = jwtService.generateAccessToken(customerUser, List.of("ROLE_PET_OWNER"));

        Pet testPet = petRepository.save(Pet.builder()
                .name("Krypto")
                .species("Dog")
                .breed("Golden Retriever")
                .status(PetStatus.ACTIVE)
                .owner(customerUser)
                .build());

        // 3. Vet
        User vetUser = userRepository.save(User.builder()
                .firstName("Doolittle")
                .lastName("Doctor")
                .email("vet_rep_" + timestamp + "@test.com")
                .phone("+9133" + (timestamp % 100000000))
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .status(UserStatus.ACTIVE)
                .tokenVersion(1)
                .build());
        userRoleRepository.save(UserRole.builder().user(vetUser).role(vetRole).build());
        vetToken = jwtService.generateAccessToken(vetUser, List.of("ROLE_VETERINARIAN"));

        testVet = veterinarianRepository.save(Veterinarian.builder()
                .user(vetUser)
                .licenseNumber("VET-REP-" + timestamp)
                .clinicName("Doolittle Animal Hospital")
                .specialization("Canine Cardiology")
                .consultationFee(BigDecimal.valueOf(800.00))
                .verificationStatus(VeterinarianVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        appointmentRepository.save(Appointment.builder()
                .appointmentNo("APT-REP-" + timestamp)
                .veterinarian(testVet)
                .pet(testPet)
                .appointmentDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .consultationFee(BigDecimal.valueOf(800.00))
                .status(AppointmentStatus.CONFIRMED)
                .reason("Cardiac Checkup")
                .build());

        // 4. Provider
        User providerUser = userRepository.save(User.builder()
                .firstName("Scissor")
                .lastName("Hands")
                .email("prov_rep_" + timestamp + "@test.com")
                .phone("+9144" + (timestamp % 100000000))
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .status(UserStatus.ACTIVE)
                .tokenVersion(1)
                .build());
        userRoleRepository.save(UserRole.builder().user(providerUser).role(providerRole).build());
        providerToken = jwtService.generateAccessToken(providerUser, List.of("ROLE_SERVICE_PROVIDER"));

        testProvider = serviceProviderRepository.save(ServiceProvider.builder()
                .user(providerUser)
                .businessName("Furry Paws Grooming")
                .providerType(ProviderType.GROOMER)
                .phone("+9144" + (timestamp % 100000000))
                .verificationStatus(ServiceProviderVerificationStatus.APPROVED)
                .isActive(true)
                .build());

        ServiceCategory groomingCategory = serviceCategoryRepository.save(ServiceCategory.builder()
                .name("Grooming Cat " + timestamp)
                .serviceType(ServiceType.GROOMING)
                .description("Test grooming")
                .isActive(true)
                .build());

        com.example.PawConnect.model.Service groomService = serviceRepository.save(com.example.PawConnect.model.Service.builder()
                .provider(testProvider)
                .name("Deluxe Fur Bath")
                .price(BigDecimal.valueOf(1200.00))
                .durationMinutes(60)
                .isActive(true)
                .build());

        serviceBookingRepository.save(ServiceBooking.builder()
                .bookingNo("SB-REP-" + timestamp)
                .provider(testProvider)
                .pet(testPet)
                .service(groomService)
                .bookingDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 0))
                .price(BigDecimal.valueOf(1200.00))
                .status(BookingStatus.CONFIRMED)
                .build());
    }

    @Test
    @DisplayName("Admin can retrieve full aggregated practitioner activity reports")
    void adminCanRetrievePractitionerActivityReport() throws Exception {
        mockMvc.perform(get("/api/admin/reports/practitioner-activity")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", isA(List.class)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("Admin can filter practitioner report by VETERINARIAN type")
    void adminCanFilterByVeterinarianType() throws Exception {
        mockMvc.perform(get("/api/admin/reports/practitioner-activity")
                        .param("type", "VETERINARIAN")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].practitionerType", everyItem(is("VETERINARIAN"))));
    }

    @Test
    @DisplayName("Admin can filter practitioner report by SERVICE_PROVIDER type")
    void adminCanFilterByServiceProviderType() throws Exception {
        mockMvc.perform(get("/api/admin/reports/practitioner-activity")
                        .param("type", "SERVICE_PROVIDER")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].practitionerType", everyItem(is("SERVICE_PROVIDER"))));
    }

    @Test
    @DisplayName("Veterinarian can retrieve own patient activity report")
    void veterinarianCanRetrieveOwnPatientReport() throws Exception {
        mockMvc.perform(get("/api/vet/reports/patients")
                        .header("Authorization", "Bearer " + vetToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.practitionerType").value("VETERINARIAN"))
                .andExpect(jsonPath("$.data.name", containsString("Doolittle")))
                .andExpect(jsonPath("$.data.clinicOrBusinessName").value("Doolittle Animal Hospital"))
                .andExpect(jsonPath("$.data.visits", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.visits[0].petName").value("Krypto"));
    }

    @Test
    @DisplayName("Service Provider can retrieve own client activity report")
    void serviceProviderCanRetrieveOwnClientReport() throws Exception {
        mockMvc.perform(get("/api/provider/reports/clients")
                        .header("Authorization", "Bearer " + providerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.practitionerType").value("SERVICE_PROVIDER"))
                .andExpect(jsonPath("$.data.clinicOrBusinessName").value("Furry Paws Grooming"))
                .andExpect(jsonPath("$.data.visits", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.visits[0].serviceOrReason").value("Deluxe Fur Bath"));
    }

    @Test
    @DisplayName("Unauthorized users are forbidden from accessing admin and professional reports")
    void unauthorizedAccessIsRejected() throws Exception {
        // Customer cannot access admin report
        mockMvc.perform(get("/api/admin/reports/practitioner-activity")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // Anonymous cannot access admin report
        mockMvc.perform(get("/api/admin/reports/practitioner-activity"))
                .andExpect(status().isUnauthorized());

        // Customer cannot access vet report
        mockMvc.perform(get("/api/vet/reports/patients")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // Customer cannot access provider report
        mockMvc.perform(get("/api/provider/reports/clients")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }
}
