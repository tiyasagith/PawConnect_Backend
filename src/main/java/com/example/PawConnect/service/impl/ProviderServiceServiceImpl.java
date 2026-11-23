package com.example.PawConnect.service.impl;

import com.example.PawConnect.dto.ServiceCategoryDto;
import com.example.PawConnect.dto.ServiceDto;
import com.example.PawConnect.dto.request.service.CreateServiceRequest;
import com.example.PawConnect.dto.request.service.UpdateServiceRequest;
import com.example.PawConnect.exception.BusinessRuleException;
import com.example.PawConnect.exception.ForbiddenException;
import com.example.PawConnect.exception.ResourceNotFoundException;
import com.example.PawConnect.model.Service;
import com.example.PawConnect.model.ServiceCategory;
import com.example.PawConnect.model.ServiceProvider;
import com.example.PawConnect.repository.ServiceBookingRepository;
import com.example.PawConnect.repository.ServiceCategoryRepository;
import com.example.PawConnect.repository.ServiceProviderRepository;
import com.example.PawConnect.repository.ServiceRepository;
import com.example.PawConnect.service.CurrentUserService;
import com.example.PawConnect.service.ProviderServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ProviderServiceServiceImpl implements ProviderServiceService {

    private final CurrentUserService currentUserService;
    private final ServiceProviderRepository serviceProviderRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceBookingRepository serviceBookingRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceDto> getProviderServices() {
        ServiceProvider provider = getAuthenticatedProvider();
        return serviceRepository.findByProviderId(provider.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceDto getProviderService(Long serviceId) {
        ServiceProvider provider = getAuthenticatedProvider();
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + serviceId));
        validateOwnership(service, provider);
        return toDto(service);
    }

    @Override
    @Transactional
    public ServiceDto createService(CreateServiceRequest request) {
        ServiceProvider provider = getAuthenticatedProvider();
        validateServiceDetails(request.getName(), request.getPrice(), request.getDurationMinutes());

        Service service = Service.builder()
                .provider(provider)
                .name(request.getName().trim())
                .description(request.getDescription())
                .price(request.getPrice())
                .durationMinutes(request.getDurationMinutes())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        setImages(service, request.getImageUrls(), request.getImageUrl());
        Service saved = serviceRepository.save(service);
        log.info("Created service id {} ({}) for provider id {}", saved.getId(), saved.getName(), provider.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public ServiceDto updateService(Long serviceId, UpdateServiceRequest request) {
        ServiceProvider provider = getAuthenticatedProvider();
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + serviceId));
        validateOwnership(service, provider);
        validateServiceDetails(request.getName(), request.getPrice(), request.getDurationMinutes());

        service.setName(request.getName().trim());
        service.setDescription(request.getDescription());
        service.setPrice(request.getPrice());
        service.setDurationMinutes(request.getDurationMinutes());
        setImages(service, request.getImageUrls(), request.getImageUrl());
        if (request.getIsActive() != null) {
            service.setIsActive(request.getIsActive());
        }

        Service updated = serviceRepository.save(service);
        log.info("Updated service id {} for provider id {}", updated.getId(), provider.getId());
        return toDto(updated);
    }

    @Override
    @Transactional
    public void deleteService(Long serviceId) {
        ServiceProvider provider = getAuthenticatedProvider();
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + serviceId));
        validateOwnership(service, provider);

        boolean hasBookings = serviceBookingRepository.existsByServiceId(serviceId);
        if (hasBookings) {
            log.info("Service id {} has historical bookings. Archiving (deactivating) rather than deleting.", serviceId);
            service.setIsActive(false);
            serviceRepository.save(service);
        } else {
            log.info("Deleting service id {} with no booking dependencies.", serviceId);
            serviceRepository.delete(service);
        }
    }

    private ServiceProvider getAuthenticatedProvider() {
        Long userId = currentUserService.getCurrentUserId();
        return serviceProviderRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Authenticated user is not registered as a service provider"));
    }

    private void validateOwnership(Service service, ServiceProvider provider) {
        if (!service.getProvider().getId().equals(provider.getId())) {
            throw new ForbiddenException("Access denied: you do not own this service");
        }
    }

    private void validateServiceDetails(String name, BigDecimal price, Integer durationMinutes) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Service title cannot be empty");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Service price must be a valid non-negative amount");
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BusinessRuleException("Service duration must be a positive number of minutes");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceCategoryDto> getCategories() {
        return serviceCategoryRepository.findByIsActiveTrue().stream()
                .map(this::toCategoryDto)
                .toList();
    }

    private ServiceCategoryDto toCategoryDto(ServiceCategory category) {
        return ServiceCategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .serviceType(category.getServiceType())
                .description(category.getDescription())
                .isActive(category.getIsActive())
                .build();
    }

    private void setImages(Service service, List<String> imageUrls, String legacyImageUrl) {
        if (imageUrls != null) {
            List<String> cleaned = imageUrls.stream().map(String::trim).distinct().toList();
            service.getImageUrls().clear();
            service.getImageUrls().addAll(cleaned);
            service.setImageUrl(cleaned.isEmpty() ? null : cleaned.get(0));
        } else if (!java.util.Objects.equals(legacyImageUrl, service.getImageUrl())) {
            service.getImageUrls().clear();
            service.setImageUrl(legacyImageUrl);
        }
    }

    private ServiceDto toDto(Service service) {
        return ServiceDto.builder()
                .id(service.getId())
                .providerId(service.getProvider() != null ? service.getProvider().getId() : null)
                .categoryName(service.getProvider().getProviderType() == com.example.PawConnect.model.ProviderType.TRAINER ? "Training" : "Grooming")
                .serviceType(service.getProvider().getProviderType() == com.example.PawConnect.model.ProviderType.TRAINER ? "TRAINING" : "GROOMING")
                .name(service.getName())
                .description(service.getDescription())
                .price(service.getPrice())
                .durationMinutes(service.getDurationMinutes())
                .imageUrl(service.getImageUrl())
                .imageUrls(service.getGalleryImageUrls())
                .isActive(service.getIsActive())
                .build();
    }
}
