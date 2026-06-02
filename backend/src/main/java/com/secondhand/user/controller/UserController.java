package com.secondhand.user.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 获取个人资料 */
    @GetMapping("/profile")
    public ApiResponse<UserService.ProfileDto> profile(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.getProfile(principal.userId()));
    }

    /** 更新个人资料 */
    @PutMapping("/profile")
    public ApiResponse<UserService.ProfileDto> updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(principal.userId(),
                req.nickname(), req.phone(), req.email()));
    }

    /** 上传头像 */
    @PutMapping("/avatar")
    public ApiResponse<String> uploadAvatar(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        String url = userService.uploadAvatar(principal.userId(), file);
        return ApiResponse.ok(url);
    }

    record UpdateProfileRequest(
            @Size(max = 50) String nickname,
            @Size(max = 20) String phone,
            @Size(max = 128) String email) {}
}
