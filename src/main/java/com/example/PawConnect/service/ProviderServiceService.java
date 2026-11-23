package com.example.PawConnect.service;

import com.example.PawConnect.dto.ServiceDto;
import com.example.PawConnect.dto.request.service.CreateServiceRequest;
import com.example.PawConnect.dto.request.service.UpdateServiceRequest;

import java.util.List;

public interface ProviderServiceService {
    List<ServiceDto> getProviderServices();

    ServiceDto getProviderService(Long serviceId);

    ServiceDto createService(CreateServiceRequest request);

    ServiceDto updateService(Long serviceId, UpdateServiceRequest request);

    void deleteService(Long serviceId);
    List<com.example.PawConnect.dto.ServiceCategoryDto> getCategories();
}
