package com.example.PawConnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDto {
    private Long id;
    private Long providerId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer durationMinutes;
    private String imageUrl;
    private List<String> imageUrls;
    private String serviceType;
    private Boolean isActive;

    public String getTitle() {
        return name;
    }
}
