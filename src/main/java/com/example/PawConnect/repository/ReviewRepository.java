package com.example.PawConnect.repository;

import com.example.PawConnect.model.Review;
import com.example.PawConnect.model.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {
    List<Review> findByUserId(Long userId);
    Page<Review> findByUserId(Long userId, Pageable pageable);
    List<Review> findByAppointmentId(Long appointmentId);
    Page<Review> findByAppointmentId(Long appointmentId, Pageable pageable);
    List<Review> findByServiceBookingId(Long serviceBookingId);
    Page<Review> findByServiceBookingId(Long serviceBookingId, Pageable pageable);
    List<Review> findByOrderItemId(Long orderItemId);
    Page<Review> findByOrderItemId(Long orderItemId, Pageable pageable);
    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    Optional<Review> findFirstByAppointmentId(Long appointmentId);
    Optional<Review> findFirstByServiceBookingId(Long serviceBookingId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.appointment.veterinarian.id = :vetId AND r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE")
    Double getAverageRatingForVeterinarian(@Param("vetId") Long vetId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.appointment.veterinarian.id = :vetId AND r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE")
    Long countReviewsForVeterinarian(@Param("vetId") Long vetId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.serviceBooking.provider.id = :providerId AND r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE")
    Double getAverageRatingForProvider(@Param("providerId") Long providerId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.serviceBooking.provider.id = :providerId AND r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE")
    Long countReviewsForProvider(@Param("providerId") Long providerId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.orderItem.product.id = :productId AND r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE")
    Double getAverageRatingForProduct(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.orderItem.product.id = :productId AND r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE")
    Long countReviewsForProduct(@Param("productId") Long productId);

    @Query("SELECT r FROM Review r WHERE r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE AND r.appointment.veterinarian.id = :vetId ORDER BY r.createdAt DESC")
    Page<Review> findVisibleByVeterinarianId(@Param("vetId") Long vetId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE AND r.serviceBooking.provider.id = :providerId ORDER BY r.createdAt DESC")
    Page<Review> findVisibleByProviderId(@Param("providerId") Long providerId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.status = com.example.PawConnect.model.ReviewStatus.VISIBLE AND r.orderItem.product.id = :productId ORDER BY r.createdAt DESC")
    Page<Review> findVisibleByProductId(@Param("productId") Long productId, Pageable pageable);
}
