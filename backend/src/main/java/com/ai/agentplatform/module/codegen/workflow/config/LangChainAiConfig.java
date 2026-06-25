package com.ai.agentplatform.module.codegen.workflow.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class LangChainAiConfig {

    @Bean
    ChatModel chatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature:0.7}") double temperature) {
        validateApiKey(apiKey);
        log.info("初始化 ChatModel: baseUrl={}, model={}", normalizeBaseUrl(baseUrl), modelName);
        return OpenAiChatModel.builder()
                .apiKey(apiKey.trim())
                .baseUrl(normalizeBaseUrl(baseUrl))
                .modelName(modelName)
                .temperature(temperature)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    StreamingChatModel streamingChatModel(
            @Value("${langchain4j.open-ai.streaming-chat-model.api-key:${langchain4j.open-ai.chat-model.api-key}}") String apiKey,
            @Value("${langchain4j.open-ai.streaming-chat-model.base-url:${langchain4j.open-ai.chat-model.base-url:https://api.openai.com/v1}}") String baseUrl,
            @Value("${langchain4j.open-ai.streaming-chat-model.model-name:${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}}") String modelName,
            @Value("${langchain4j.open-ai.streaming-chat-model.temperature:${langchain4j.open-ai.chat-model.temperature:0.7}}") double temperature) {
        validateApiKey(apiKey);
        log.info("初始化 StreamingChatModel: baseUrl={}, model={}", normalizeBaseUrl(baseUrl), modelName);
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey.trim())
                .baseUrl(normalizeBaseUrl(baseUrl))
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    private static void validateApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey) || apiKey.contains("你的")) {
            throw new IllegalStateException("请在 application-local.yml 配置有效的 langchain4j.open-ai.chat-model.api-key");
        }
    }

    /** DeepSeek 等 OpenAI 兼容接口需要 /v1 后缀 */
    private static String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        return url;
    }
}
