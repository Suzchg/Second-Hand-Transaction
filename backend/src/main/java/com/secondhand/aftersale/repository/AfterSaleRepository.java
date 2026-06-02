package com.secondhand.aftersale.repository;

import com.secondhand.aftersale.entity.AfterSaleRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AfterSaleRepository extends JpaRepository<AfterSaleRequest, Long> {
    Optional<AfterSaleRequest> findByIdAndOrderId(Long id, Long orderId);
}
