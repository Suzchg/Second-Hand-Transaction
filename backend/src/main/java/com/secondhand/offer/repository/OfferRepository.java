package com.secondhand.offer.repository;

import com.secondhand.offer.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<Offer> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<Offer> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    long countByProductIdAndStatus(Long productId, com.secondhand.offer.entity.OfferStatus status);
}
