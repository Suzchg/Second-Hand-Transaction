package com.secondhand.aftersale.repository;

import com.secondhand.aftersale.entity.AfterSaleRequest;
import com.secondhand.aftersale.entity.AfterSaleStatus;
import com.secondhand.aftersale.entity.AfterSaleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AfterSaleRepository extends JpaRepository<AfterSaleRequest, Long> {

    Page<AfterSaleRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AfterSaleRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<AfterSaleRequest> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<AfterSaleRequest> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<AfterSaleRequest> findByOrderIdAndStatusNotIn(Long orderId, List<AfterSaleStatus> statuses);

    /** 超时处理：查找截止时间已过且未完结的售后单 */
    List<AfterSaleRequest> findByStatusNotInAndDeadlineAtBefore(
            List<AfterSaleStatus> statuses, LocalDateTime before);

    /** 管理员：按状态分页查询 */
    Page<AfterSaleRequest> findByStatusOrderByCreatedAtDesc(AfterSaleStatus status, Pageable pageable);

    /** 管理员：按售后类型分页查询 */
    Page<AfterSaleRequest> findByRequestTypeOrderByCreatedAtDesc(AfterSaleType type, Pageable pageable);

    /** 管理员：按状态计数 */
    long countByStatus(AfterSaleStatus status);
}
