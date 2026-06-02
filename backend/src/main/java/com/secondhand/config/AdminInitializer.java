package com.secondhand.config;

import com.secondhand.auth.entity.*;
import com.secondhand.auth.repository.UserIdentityRepository;
import com.secondhand.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final UserIdentityRepository identityRepo;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepo,
                            UserIdentityRepository identityRepo,
                            PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.identityRepo = identityRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 始终确保有一个 "admin" 管理员账号存在
        boolean hasAdminIdentity = identityRepo
                .findByIdentityTypeAndIdentifier(IdentityType.PHONE, "13800000000")
                .isPresent();
        if (hasAdminIdentity) return;

        User admin = new User();
        admin.setNickname("管理员");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setRole(Role.ADMIN);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        admin = userRepo.save(admin);

        LocalDateTime now = LocalDateTime.now();
        UserIdentity identity = new UserIdentity();
        identity.setUser(admin);
        identity.setIdentityType(IdentityType.PHONE);
        identity.setIdentifier("13800000000");
        identity.setVerified(true);
        identity.setCreatedAt(now);
        identity.setUpdatedAt(now);
        identityRepo.save(identity);
    }
}
