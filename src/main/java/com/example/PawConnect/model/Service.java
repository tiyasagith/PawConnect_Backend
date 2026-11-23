package com.example.PawConnect.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "services", indexes = {
    @Index(name = "idx_services_provider_id", columnList = "provider_id"),
    @Index(name = "idx_services_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"provider", "serviceBookings"})
@EntityListeners(AuditingEntityListener.class)
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ServiceProvider provider;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "service_images", joinColumns = @JoinColumn(name = "service_id"))
    @OrderColumn(name = "position")
    @Column(name = "image_url", nullable = false, length = 500)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    public List<String> getGalleryImageUrls() {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            return List.copyOf(imageUrls);
        }
        return imageUrl == null || imageUrl.isBlank() ? List.of() : List.of(imageUrl);
    }

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Do NOT cascade delete bookings when a service is deactivated or removed
    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    private List<ServiceBooking> serviceBookings;
}
