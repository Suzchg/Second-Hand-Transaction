package com.secondhand.rating.repository;

import com.secondhand.rating.entity.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByOrderId(Long orderId);

    Page<Rating> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM Rating r WHERE r.sellerId = :sellerId")
    Double getAverageScoreBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.sellerId = :sellerId")
    Long getCountBySellerId(@Param("sellerId") Long sellerId);
}
