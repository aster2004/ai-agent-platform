package com.ai.agentplatform.module.codegen.util;

import org.springframework.stereotype.Component;
/**
 * 当前登录用户工具类（并行开发Mock版本）
 * TODO D3波次1集成：对接用户模块SecurityContext，解析JWT令牌获取真实登录用户ID
 */
@Component
public class CurrentUserUtil {

    /**
     * 获取当前操作人userId，Mock固定返回测试用户1
     */
    public static Long getCurrentUserId() {
        // 并行开发阶段固定Mock值，集成后替换JWT解析逻辑
        return 1L;
    }
}