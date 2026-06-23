package com.ai.agentplatform.module.codegen.util;

import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 请求参数自动兜底填充工具
 * 空参数自动赋值Mock默认值，避免空指针异常
 */
@Component
public class ParamFillUtil {

    // 从yml配置读取系统默认大模型名称
    @Value("${ai.model.default-name:" + CodeGenConstant.MODEL_DEEPSEEK + "}")
    private String defaultModelName;

    /**
     * 填充应用ID：入参为空返回Mock默认值1L
     */
    public Long fillAppId(Long appId) {
        return appId == null ? CodeGenConstant.MOCK_DEFAULT_APP_ID : appId;
    }

    public String fillModel(String modelName) {
        // 如果前端传空/null，默认使用 deepseek
        if (modelName == null || modelName.isBlank()) {
            return CodeGenConstant.MODEL_DEEPSEEK;
        }
        return modelName;
    }

    /**
     * 填充模型名称：入参空/空白读取yml默认模型
     */
    public String fillModelName(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return defaultModelName;
        }
        return modelName;
    }

    /**
     * 判断会话ID是否为空（无对话上下文）
     */
    public boolean sessionIdIsEmpty(Long sessionId) {
        return sessionId == null;
    }
}