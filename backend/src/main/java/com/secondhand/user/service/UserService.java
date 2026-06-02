package com.secondhand.user.service;

import com.secondhand.auth.entity.User;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final Path uploadDir = Paths.get(System.getProperty("user.home"), ".secondhand", "uploads", "avatars");

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ignored) {}
    }

    public record ProfileDto(Long userId, String nickname, String phone, String email, String avatarUrl) {}

    @Transactional(readOnly = true)
    public ProfileDto getProfile(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        return new ProfileDto(user.getId(), user.getNickname(), user.getPhone(), user.getEmail(), user.getAvatarUrl());
    }

    @Transactional
    public ProfileDto updateProfile(Long userId, String nickname, String phone, String email) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (nickname != null && !nickname.isBlank()) user.setNickname(nickname.trim());
        if (phone != null) user.setPhone(phone.trim());
        if (email != null) user.setEmail(email.trim().toLowerCase());
        user.setUpdatedAt(LocalDateTime.now());
        userRepo.save(user);
        return new ProfileDto(user.getId(), user.getNickname(), user.getPhone(), user.getEmail(), user.getAvatarUrl());
    }

    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = "avatar-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path dest = uploadDir.resolve(filename);
            file.transferTo(dest.toFile());
            String url = "/uploads/avatars/" + filename;
            user.setAvatarUrl(url);
            user.setUpdatedAt(LocalDateTime.now());
            userRepo.save(user);
            return url;
        } catch (IOException e) {
            throw new AppException("UPLOAD_ERROR", "头像上传失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".png";
        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        return switch (ext) {
            case ".jpg", ".jpeg", ".png", ".webp", ".gif" -> ext;
            default -> ".png";
        };
    }
}
