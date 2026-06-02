package com.secondhand.favorite.service;

import com.secondhand.favorite.entity.Favorite;
import com.secondhand.favorite.repository.FavoriteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepo;

    public FavoriteService(FavoriteRepository favoriteRepo) {
        this.favoriteRepo = favoriteRepo;
    }

    @Transactional
    public void addFavorite(Long userId, Long productId) {
        if (favoriteRepo.existsByUserIdAndProductId(userId, productId)) return;
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setProductId(productId);
        f.setCreatedAt(LocalDateTime.now());
        favoriteRepo.save(f);
    }

    @Transactional
    public void removeFavorite(Long userId, Long productId) {
        favoriteRepo.deleteByUserIdAndProductId(userId, productId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorited(Long userId, Long productId) {
        return favoriteRepo.existsByUserIdAndProductId(userId, productId);
    }

    @Transactional(readOnly = true)
    public Page<Favorite> getMyFavorites(Long userId, int page, int size) {
        return favoriteRepo.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, Math.min(size, 50)));
    }
}
