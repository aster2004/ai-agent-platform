package com.ai.agentplatform.module.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型yml配置读取类
 * 子任务1目录梳理创建骨架，子任务3完善使用
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    private String defaultName;
    private DeepSeekConfig deepseek;
    private OpenAiConfig openai;
    private BaiLianConfig bailian;

    @Data
    public static class DeepSeekConfig {
        private String baseUrl;
        private String apiKey;
        private String modelName;
    }

    @Data
    public static class OpenAiConfig {
        private String baseUrl;
        private String apiKey;
        private String modelName;
    }

    @Data
    public static class BaiLianConfig {
        private String baseUrl;
        private String apiKey;
        private String modelName;
    }
}