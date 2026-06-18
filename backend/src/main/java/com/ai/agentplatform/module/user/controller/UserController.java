package com.ai.agentplatform.module.user.controller;

import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.user.config.JwtUtil;
import com.ai.agentplatform.module.user.dto.UserLoginRequest;
import com.ai.agentplatform.module.user.dto.UserRegisterRequest;
import com.ai.agentplatform.module.user.service.UserService;
import com.ai.agentplatform.module.user.vo.LoginVO;
import com.ai.agentplatform.module.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

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

    @Operation(summary = "用户列表（管理员）")
    @GetMapping("/admin/list")
    public Result<Page<UserVO>> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
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
}
