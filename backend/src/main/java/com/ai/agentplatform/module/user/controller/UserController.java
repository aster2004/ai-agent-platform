package com.ai.agentplatform.module.user.controller;

import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.user.config.JwtUtil;
import com.ai.agentplatform.module.user.dto.UserLoginRequest;
import com.ai.agentplatform.module.user.dto.UserProfileRequest;
import com.ai.agentplatform.module.user.dto.UserRegisterRequest;
import com.ai.agentplatform.module.user.service.TokenBlacklistService;
import com.ai.agentplatform.module.user.service.UserService;
import com.ai.agentplatform.module.user.vo.LoginVO;
import com.ai.agentplatform.module.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody UserRegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginRequest request) {
        LoginVO loginVO = userService.login(request);
        loginVO.setToken(jwtUtil.generateToken(loginVO.getUserId(), loginVO.getRole()));
        return Result.success(loginVO);
    }

    @Operation(summary = "获取当前用户")
    @GetMapping("/current")
    public Result<UserVO> getCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(userService.getById(userId));
    }

    @Operation(summary = "用户注销")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            tokenBlacklistService.blacklist(token);
            System.out.println("logout - token blacklisted");
        }
        return Result.success();
    }

    @Operation(summary = "用户列表（管理员）")
    @GetMapping("/admin/list")
    public Result<Page<UserVO>> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return Result.success(userService.searchByUsername(keyword.trim(), page, size));
        }
        return Result.success(userService.list(page, size));
    }

    @Operation(summary = "禁用用户（管理员）")
    @PutMapping("/admin/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        userService.updateStatus(id, "disabled");
        return Result.success();
    }

    @Operation(summary = "启用用户（管理员）")
    @PutMapping("/admin/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        userService.updateStatus(id, "normal");
        return Result.success();
    }

    @Operation(summary = "删除用户（管理员）")
    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@RequestBody UserProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) authentication.getPrincipal();
        System.out.println("updateProfile - userId: " + userId + ", nickname: " + request.getNickname() + ", phone: " + request.getPhone() + ", email: " + request.getEmail());
        return Result.success(userService.updateProfile(userId, request.getNickname(), request.getPhone(), request.getEmail()));
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar/upload")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) authentication.getPrincipal();
        String avatarUrl = userService.uploadAvatar(userId, file);
        return Result.success(avatarUrl);
    }

    @Operation(summary = "获取头像")
    @GetMapping("/avatar/{filename}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String filename) {
        byte[] content = userService.getAvatar(filename);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = MediaType.IMAGE_JPEG_VALUE;
        if (filename.toLowerCase().endsWith(".png")) {
            contentType = MediaType.IMAGE_PNG_VALUE;
        } else if (filename.toLowerCase().endsWith(".gif")) {
            contentType = MediaType.IMAGE_GIF_VALUE;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }
}
