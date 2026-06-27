package com.ai.agentplatform.module.codegen.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 代码生成异步线程上下文：在 SSE / 后台线程中传递当前请求的 Authorization 头，
 * 供 AppSyncHelper 通过 RestTemplate 以正确用户身份调用 /api/app。
 */
public final class CodeGenRequestContext {

    private static final ThreadLocal<String> AUTHORIZATION = new ThreadLocal<>();

    private CodeGenRequestContext() {
    }

    /**
     * 在 Web 请求线程上捕获 Authorization 头（需在异步提交前调用）。
     */
    public static String captureAuthorization() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && !authHeader.isBlank()) {
                    return authHeader;
                }
            }
        } catch (Exception ignored) {
            // 非 Web 上下文
        }
        return null;
    }

    /**
     * 解析当前线程可用的 Authorization：ThreadLocal → 请求上下文。
     */
    public static String resolveAuthorization() {
        String fromThread = AUTHORIZATION.get();
        if (fromThread != null && !fromThread.isBlank()) {
            return fromThread;
        }
        return captureAuthorization();
    }

    public static void runWithAuthorization(String authorization, Runnable action) {
        String previous = AUTHORIZATION.get();
        try {
            if (authorization != null && !authorization.isBlank()) {
                AUTHORIZATION.set(authorization);
            }
            action.run();
        } finally {
            if (previous != null) {
                AUTHORIZATION.set(previous);
            } else {
                AUTHORIZATION.remove();
            }
        }
    }

    /** 测试或异常兜底时清理 ThreadLocal */
    public static void clear() {
        AUTHORIZATION.remove();
    }
}
