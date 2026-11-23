package com.example.PawConnect.controller;

import com.example.PawConnect.dto.ServiceDto;
import com.example.PawConnect.dto.request.service.CreateServiceRequest;
import com.example.PawConnect.dto.request.service.UpdateServiceRequest;
import com.example.PawConnect.response.ApiResponse;
import com.example.PawConnect.service.ProviderServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/provider/services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderServiceController {

    private final ProviderServiceService providerServiceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceDto>>> getServices() {
        return ResponseEntity.ok(ApiResponse.ok(
                providerServiceService.getProviderServices(),
                "Provider services retrieved successfully"
        ));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<com.example.PawConnect.dto.ServiceCategoryDto>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(
                providerServiceService.getCategories(),
                "Service categories retrieved successfully"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceDto>> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                providerServiceService.getProviderService(id),
                "Provider service retrieved successfully"
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceDto>> createService(
            @Valid @RequestBody CreateServiceRequest request
    ) {
        ServiceDto created = providerServiceService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                created,
                "Service created successfully"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceDto>> updateService(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                providerServiceService.updateService(id, request),
                "Service updated successfully"
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable Long id) {
        providerServiceService.deleteService(id);
        return ResponseEntity.ok(ApiResponse.ok(
                null,
                "Service removed or deactivated successfully"
        ));
    }
}
