package com.secondhand.favorite.repository;

import com.secondhand.favorite.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    void deleteByUserIdAndProductId(Long userId, Long productId);

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 获取用户收藏的所有商品 ID */
    @org.springframework.data.jpa.repository.Query("SELECT f.productId FROM Favorite f WHERE f.userId = :userId")
    java.util.List<Long> findProductIdsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
