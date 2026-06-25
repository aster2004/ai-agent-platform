package com.ai.agentplatform.module.codegen.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户工具类
 * 从成员1 JwtAuthFilter 设置的 SecurityContext 中提取登录用户 ID
 *
 * <p>成员1 的 JwtAuthFilter 将 userId (Long) 设为 Authentication.principal，
 * 若请求无有效 JWT 则 SecurityContext 为 anonymousUser。</p>
 */
@Component
public class CurrentUserUtil {

    /**
     * 从 SecurityContext 获取当前登录用户 ID
     * 成员1 JwtAuthFilter 将 Long 类型 userId 设为 principal
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            // 无有效 JWT — 开发阶段兜底为 Mock 用户 1，生产环境应直接抛异常
            return 1L;
        }
        Object principal = auth.getPrincipal();
        // 成员1 JwtAuthFilter 将 userId(Long) 直接设为主凭证
        if (principal instanceof Long userId) {
            return userId;
        }
        // 兼容：部分场景 principal 为 userId 字符串
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new RuntimeException("无法解析用户ID: " + str);
            }
        }
        throw new RuntimeException("无法获取登录用户ID，未知的 principal 类型: " + principal.getClass().getName());
    }
}