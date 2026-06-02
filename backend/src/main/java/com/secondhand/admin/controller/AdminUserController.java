package com.secondhand.admin.controller;

import com.secondhand.admin.OnlineUserTracker;
import com.secondhand.admin.security.TokenBlacklist;
import com.secondhand.auth.entity.User;
import com.secondhand.auth.entity.UserStatus;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.common.ApiResponse;
import com.secondhand.common.AppException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepo;
    private final TokenBlacklist blacklist;
    private final OnlineUserTracker onlineUserTracker;

    public AdminUserController(UserRepository userRepo, TokenBlacklist blacklist,
                               OnlineUserTracker onlineUserTracker) {
        this.userRepo = userRepo;
        this.blacklist = blacklist;
        this.onlineUserTracker = onlineUserTracker;
    }

    /** 分页查询用户 */
    @GetMapping
    public ApiResponse<Page<User>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<User> result;
        if (keyword != null && !keyword.isBlank()) {
            result = userRepo.findByNicknameContainingIgnoreCase(keyword.trim(), PageRequest.of(page, size));
        } else {
            result = userRepo.findAll(PageRequest.of(page, size));
        }
        return ApiResponse.ok(result);
    }

    /** 用户详情 */
    @GetMapping("/{id}")
    public ApiResponse<User> detail(@PathVariable Long id) {
        return ApiResponse.ok(userRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND)));
    }

    /** 禁用/启用用户 */
    @PutMapping("/{id}/disable")
    public ApiResponse<User> toggleDisable(@PathVariable Long id, @RequestParam boolean disabled) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        user.setStatus(disabled ? UserStatus.DISABLED : UserStatus.ACTIVE);
        return ApiResponse.ok(userRepo.save(user));
    }

    /** 强制下线 */
    @PostMapping("/{id}/kick")
    public ApiResponse<String> kick(@PathVariable Long id) {
        userRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        blacklist.add(id);
        onlineUserTracker.remove(id);
        return ApiResponse.ok("用户已被强制下线");
    }

    /** 获取在线用户 ID */
    @GetMapping("/online")
    public ApiResponse<Set<Long>> onlineUsers() {
        return ApiResponse.ok(onlineUserTracker.getActiveUserIds(10));
    }
}
