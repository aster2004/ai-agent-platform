package com.ai.agentplatform.module.codegen.util;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Component;
/**
 * 当前登录用户工具类（并行开发Mock版本）
 * TODO D3波次1集成：对接用户模块SecurityContext，解析JWT令牌获取真实登录用户ID
 */
@Component
public class CurrentUserUtil {
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 未登录拦截
        if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("用户未登录，请携带Token访问接口");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        // 方案1：principal是userId字符串
        if (principal instanceof String str) {
            return Long.parseLong(str);
        }
        // 方案2：反射读取自定义UserDetails的getUserId，不用导入对方类
        try {
            java.lang.reflect.Method getIdMethod = principal.getClass().getDeclaredMethod("getUserId");
            return (Long) getIdMethod.invoke(principal);
        } catch (Exception e) {
            throw new RuntimeException("无法获取登录用户ID，请检查登录令牌", e);
        }
    }
}