package com.secondhand.auth.service;

import com.secondhand.admin.OnlineUserTracker;
import com.secondhand.auth.dto.LoginRequest;
import com.secondhand.auth.dto.RegisterRequest;
import com.secondhand.auth.entity.*;
import com.secondhand.auth.repository.UserIdentityRepository;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.auth.security.JwtService;
import com.secondhand.common.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock UserIdentityRepository identityRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    OnlineUserTracker onlineUserTracker = new OnlineUserTracker();
    AuthService authService;

    @BeforeEach
    void buildService() {
        authService = new AuthService(userRepo, identityRepo, passwordEncoder, jwtService, onlineUserTracker);
    }

    /** 通过反射设置 JPA 实体的 ID（无 setter） */
    static void setId(User user, Long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception ignored) {}
    }

    @Nested
    @DisplayName("register - 注册")
    class Register {

        @Test
        @DisplayName("使用新手机号注册应成功返回 token")
        void shouldRegisterWithNewPhone() {
            RegisterRequest req = new RegisterRequest("PHONE", "13800138000", "password123");
            when(identityRepo.existsByIdentityTypeAndIdentifier(eq(IdentityType.PHONE), anyString()))
                    .thenReturn(false);
            when(userRepo.existsByRole(Role.ADMIN)).thenReturn(true);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
            when(userRepo.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                setId(u, 1L);
                return u;
            });
            when(jwtService.createAccessToken(eq(1L), anyString())).thenReturn("jwt.token.here");

            var result = authService.register(req);

            assertNotNull(result);
            assertEquals("jwt.token.here", result.accessToken());
            verify(identityRepo).save(any(UserIdentity.class));
        }

        @Test
        @DisplayName("已注册手机号重复注册应抛出 CONFLICT")
        void shouldRejectDuplicatePhone() {
            RegisterRequest req = new RegisterRequest("PHONE", "13800138000", "password123");
            when(identityRepo.existsByIdentityTypeAndIdentifier(eq(IdentityType.PHONE), anyString()))
                    .thenReturn(true);

            AppException ex = assertThrows(AppException.class, () -> authService.register(req));
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
            assertEquals("IDENTITY_EXISTS", ex.getCode());
        }
    }

    @Nested
    @DisplayName("login - 登录")
    class Login {

        private User user;
        private UserIdentity identity;

        @BeforeEach
        void setUp() {
            user = new User();
            user.setPasswordHash("$2a$encoded");
            user.setStatus(UserStatus.ACTIVE);
            user.setNickname("测试用户");
            user.setRole(Role.USER);
            setId(user, 1L);

            identity = new UserIdentity();
            identity.setUser(user);
        }

        @Test
        @DisplayName("正确密码登录应返回 token")
        void shouldLoginWithCorrectPassword() {
            LoginRequest req = new LoginRequest("PHONE", "13800138000", "correct");
            when(identityRepo.findByIdentityTypeAndIdentifier(eq(IdentityType.PHONE), anyString()))
                    .thenReturn(Optional.of(identity));
            when(passwordEncoder.matches("correct", "$2a$encoded")).thenReturn(true);
            when(jwtService.createAccessToken(eq(1L), anyString())).thenReturn("jwt.token.here");

            var result = authService.login(req);

            assertNotNull(result);
            assertEquals("jwt.token.here", result.accessToken());
            assertEquals("测试用户", result.nickname());
        }

        @Test
        @DisplayName("错误密码登录应抛出 UNAUTHORIZED")
        void shouldRejectWrongPassword() {
            LoginRequest req = new LoginRequest("PHONE", "13800138000", "wrong");
            when(identityRepo.findByIdentityTypeAndIdentifier(eq(IdentityType.PHONE), anyString()))
                    .thenReturn(Optional.of(identity));
            when(passwordEncoder.matches("wrong", "$2a$encoded")).thenReturn(false);

            AppException ex = assertThrows(AppException.class, () -> authService.login(req));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
        }

        @Test
        @DisplayName("被禁用账号登录应抛出 FORBIDDEN")
        void shouldRejectDisabledAccount() {
            user.setStatus(UserStatus.DISABLED);
            LoginRequest req = new LoginRequest("PHONE", "13800138000", "correct");
            when(identityRepo.findByIdentityTypeAndIdentifier(eq(IdentityType.PHONE), anyString()))
                    .thenReturn(Optional.of(identity));
            when(passwordEncoder.matches("correct", "$2a$encoded")).thenReturn(true);

            AppException ex = assertThrows(AppException.class, () -> authService.login(req));
            assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        }
    }
}
