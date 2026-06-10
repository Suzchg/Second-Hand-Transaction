package com.secondhand.product.repository;

import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 用户端：只列出在售且库存>0的商品 */
    Page<Product> findByStatusAndQuantityGreaterThan(ProductStatus status, int minQty, Pageable pageable);

    /** 管理端：列出指定状态的所有商品（含售罄） */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.quantity > 0 AND p.categoryId IN :categoryIds")
    Page<Product> findByStatusAndCategoryIdIn(@Param("status") ProductStatus status,
                                              @Param("categoryIds") List<Long> categoryIds,
                                              Pageable pageable);

    Page<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Product> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ProductStatus status);

    // ---- 搜索（仅在售+有库存） ----

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.quantity > 0 AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> findByStatusAndQuantityAndTitleContaining(@Param("status") ProductStatus status,
                                                             @Param("keyword") String keyword,
                                                             Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.quantity > 0 AND p.categoryId IN :categoryIds AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> findByStatusAndCategoryIdInAndTitleContaining(@Param("status") ProductStatus status,
                                                                  @Param("categoryIds") List<Long> categoryIds,
                                                                  @Param("keyword") String keyword,
                                                                  Pageable pageable);

    // ---- 卖家自己的商品 ----

    Page<Product> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    // ---- 某卖家在售商品（公开查看） ----

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.quantity > 0 AND p.sellerId = :sellerId")
    Page<Product> findByStatusAndQuantityGreaterThanAndSellerId(
            @Param("status") ProductStatus status,
            @Param("sellerId") Long sellerId,
            Pageable pageable);

    // ---- 推荐 ----

    /**
     * 推荐总览：展示所有在售商品，但用户偏好分类的商品排在最前面。
     * 如果 favCategoryIds 为空，则按时间倒序返回所有商品。
     */
    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.quantity > 0 " +
           "ORDER BY CASE WHEN p.categoryId IN :favCategoryIds THEN 0 ELSE 1 END, p.createdAt DESC")
    Page<Product> findRecommended(@Param("status") ProductStatus status,
                                   @Param("favCategoryIds") List<Long> favCategoryIds,
                                   Pageable pageable);
}
