package com.secondhand.auth.service;

import com.secondhand.admin.OnlineUserTracker;
import com.secondhand.auth.dto.AuthResponse;
import com.secondhand.auth.dto.ChangePasswordRequest;
import com.secondhand.auth.dto.LoginRequest;
import com.secondhand.auth.dto.RegisterRequest;
import com.secondhand.auth.entity.IdentityType;
import com.secondhand.auth.entity.Role;
import com.secondhand.auth.entity.User;
import com.secondhand.auth.entity.UserIdentity;
import com.secondhand.auth.entity.UserStatus;
import com.secondhand.auth.repository.UserIdentityRepository;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.auth.security.JwtService;
import com.secondhand.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务。
 * 负责注册、登录、改密等核心业务逻辑。
 *
 * 设计要点：
 * - 一个用户可绑定多种登录方式（手机号 + 邮箱）
 * - 密码使用 BCrypt 加密存储
 * - 登录成功返回 JWT 访问令牌
 * - 标识符归一化：邮箱统一转小写，手机号去除首尾空白
 */
@Service
public class AuthService {

    private final UserRepository userRepo;
    private final UserIdentityRepository identityRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final OnlineUserTracker onlineUserTracker;

    public AuthService(UserRepository userRepo,
                       UserIdentityRepository identityRepo,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       OnlineUserTracker onlineUserTracker) {
        this.userRepo = userRepo;
        this.identityRepo = identityRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.onlineUserTracker = onlineUserTracker;
    }

    /**
     * 用户注册。
     * 创建 User + UserIdentity，返回 JWT Token。
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        IdentityType type = IdentityType.valueOf(req.identityType());
        String normalized = normalize(type, req.identifier());

        // 检查该标识是否已被注册
        if (identityRepo.existsByIdentityTypeAndIdentifier(type, normalized)) {
            throw new AppException("IDENTITY_EXISTS",
                    type == IdentityType.PHONE ? "该手机号已注册" : "该邮箱已注册",
                    HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();

        // 创建用户
        User user = new User();
        user.setNickname("新用户");
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus(UserStatus.ACTIVE);
        // 同步 phone/email 到 User 实体
        if (type == IdentityType.PHONE) {
            user.setPhone(normalized);
        } else {
            user.setEmail(normalized);
        }
        // 如果系统中没有管理员，注册者自动成为管理员
        if (!userRepo.existsByRole(Role.ADMIN)) {
            user.setRole(Role.ADMIN);
        }
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepo.save(user);

        // 创建登录标识
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setIdentityType(type);
        identity.setIdentifier(normalized);
        identity.setVerified(false);
        identity.setCreatedAt(now);
        identity.setUpdatedAt(now);
        identityRepo.save(identity);

        String token = jwtService.createAccessToken(user.getId(), user.getRole().name());
        onlineUserTracker.heartbeat(user.getId());
        return new AuthResponse(token, user.getId(), user.getNickname(), user.getRole().name());
    }

    /**
     * 用户登录。
     * 验证标识 + 密码，返回 JWT Token。
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        IdentityType type = IdentityType.valueOf(req.identityType());
        String normalized = normalize(type, req.identifier());

        // 查找登录标识
        UserIdentity identity = identityRepo
                .findByIdentityTypeAndIdentifier(type, normalized)
                .orElseThrow(() -> new AppException("INVALID_CREDENTIALS",
                        "账号或密码错误", HttpStatus.UNAUTHORIZED));

        User user = identity.getUser();

        // 验证密码
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AppException("INVALID_CREDENTIALS",
                    "账号或密码错误", HttpStatus.UNAUTHORIZED);
        }

        // 检查账号状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("FORBIDDEN", "账号已被禁用", HttpStatus.FORBIDDEN);
        }

        String token = jwtService.createAccessToken(user.getId(), user.getRole().name());
        onlineUserTracker.heartbeat(user.getId());
        return new AuthResponse(token, user.getId(), user.getNickname(), user.getRole().name());
    }

    /**
     * 修改密码。
     * 需要提供旧密码验证身份。
     */
    @Transactional
    public void changePassword(long userId, ChangePasswordRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new AppException("INVALID_CREDENTIALS", "旧密码不正确", HttpStatus.UNAUTHORIZED);
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepo.save(user);
    }

    /**
     * 获取当前用户信息。
     */
    @Transactional(readOnly = true)
    public AuthResponse getUserInfo(long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        return new AuthResponse(null, user.getId(), user.getNickname(), user.getRole().name());
    }

    /**
     * 标识符归一化：邮箱转小写，手机号去首尾空白。
     */
    private String normalize(IdentityType type, String identifier) {
        String trimmed = identifier == null ? "" : identifier.trim();
        return type == IdentityType.EMAIL ? trimmed.toLowerCase() : trimmed;
    }
}
