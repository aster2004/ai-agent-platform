package com.ai.agentplatform.common.util;

import com.ai.agentplatform.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthHelper {

    public void requireAdmin(String role) {
        if (!"admin".equalsIgnoreCase(role)) {
            throw new BusinessException("需要管理员权限");
        }
    }
}
