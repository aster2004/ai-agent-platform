package com.ai.agentplatform.module.codegen.service.factory;

import com.ai.agentplatform.module.codegen.config.LlmConfig;
import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Slf4j
@Component
public class LlmModelFactory {
    @Resource
    private LlmConfig llmConfig;

    // 缓存默认模型实例，只初始化一次
    private ChatModel cachedDefaultChatModel;
    private StreamingChatModel cachedDefaultStreamModel;

    /**
     * 获取同步对话模型（无递归入口）
     */
    public ChatModel getChatModel(String modelName) {
        String name = modelName == null ? "" : modelName.toLowerCase().trim();
        return switch (name) {
            case CodeGenConstant.MODEL_DEEPSEEK -> buildDeepSync();
            case CodeGenConstant.MODEL_OPENAI -> buildOpenSync();
            case CodeGenConstant.MODEL_BAILIAN -> buildBaiSync();
            default -> {
                log.warn("未知模型[{}]，加载系统默认模型", modelName);
                yield getDefaultChatModel();
            }
        };
    }

    /**
     * 获取流式对话模型（无递归入口）
     */
    public StreamingChatModel getStreamModel(String modelName) {
        String name = modelName == null ? "" : modelName.toLowerCase().trim();
        return switch (name) {
            case CodeGenConstant.MODEL_DEEPSEEK -> buildDeepStream();
            case CodeGenConstant.MODEL_OPENAI -> buildOpenStream();
            case CodeGenConstant.MODEL_BAILIAN -> buildBaiStream();
            default -> {
                log.warn("未知流式模型[{}]，加载系统默认模型", modelName);
                yield getDefaultStreamModel();
            }
        };
    }

    // ===================== 默认模型获取（核心修复：杜绝循环递归 =====================
    private ChatModel getDefaultChatModel() {
        if (cachedDefaultChatModel != null) {
            return cachedDefaultChatModel;
        }
        String defaultModelName = llmConfig.getDefaultName();
        // 空值兜底
        if (defaultModelName == null || defaultModelName.isBlank()) {
            log.warn("yml未配置llm.default-name，强制使用{}兜底", CodeGenConstant.FALLBACK_MODEL);
            defaultModelName =CodeGenConstant.MODEL_DEEPSEEK;
        }
        defaultModelName = defaultModelName.toLowerCase().trim();
        try {
            cachedDefaultChatModel = switch (defaultModelName) {
                case CodeGenConstant.MODEL_DEEPSEEK -> buildDeepSyncNoFallback();
                case CodeGenConstant.MODEL_BAILIAN -> buildBaiSyncNoFallback();
                case CodeGenConstant.MODEL_OPENAI -> buildOpenSyncNoFallback();
                default -> {
                    log.warn("配置默认模型[{}]不支持，切换deepseek", defaultModelName);
                    yield buildDeepSyncNoFallback();
                }
            };
        } catch (Exception e) {
            log.error("配置默认模型创建失败，强制兜底DeepSeek", e);
            cachedDefaultChatModel = buildDeepSyncNoFallback();
        }
        return cachedDefaultChatModel;
    }

    private StreamingChatModel getDefaultStreamModel() {
        if (cachedDefaultStreamModel != null) {
            return cachedDefaultStreamModel;
        }
        String defaultModelName = llmConfig.getDefaultName();
        if (defaultModelName == null || defaultModelName.isBlank()) {
            log.warn("yml未配置llm.default-name，流式强制deepseek");
            defaultModelName = CodeGenConstant.MODEL_DEEPSEEK;
        }
        defaultModelName = defaultModelName.toLowerCase().trim();
        try {
            cachedDefaultStreamModel = switch (defaultModelName) {
                case CodeGenConstant.MODEL_DEEPSEEK -> buildDeepStreamNoFallback();
                case CodeGenConstant.MODEL_BAILIAN -> buildBaiStreamNoFallback();
                case CodeGenConstant.MODEL_OPENAI -> buildOpenStreamNoFallback();
                default -> {
                    log.warn("默认流式模型[{}]不支持，切换deepseek", defaultModelName);
                    yield buildDeepStreamNoFallback();
                }
            };
        } catch (Exception e) {
            log.error("默认流式模型创建失败，兜底DeepSeek", e);
            cachedDefaultStreamModel = buildDeepStreamNoFallback();
        }
        return cachedDefaultStreamModel;
    }

    // ===================== 无降级底层构建方法（新增，不会递归） =====================
    private ChatModel buildDeepSyncNoFallback() {
        LlmConfig.DeepSeekConfig cfg = llmConfig.getDeepseek();
        // 增加判空
        if(cfg == null){
            throw new RuntimeException("yml未配置llm.deepseek，无法构建DeepSeek模型");
        }
        return OpenAiChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    private ChatModel buildBaiSyncNoFallback() {
        LlmConfig.BaiLianConfig cfg = llmConfig.getBailian();
        // 增加判空
        if(cfg == null){
            throw new RuntimeException("yml未配置llm.bailian，无法构建bailian模型");
        }
        return OpenAiChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    private ChatModel buildOpenSyncNoFallback() {
        LlmConfig.OpenAiConfig cfg = llmConfig.getOpenai();
        // 增加判空
        if(cfg == null){
            throw new RuntimeException("yml未配置llm.openai，无法构建openai模型");
        }
        return OpenAiChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    private StreamingChatModel buildDeepStreamNoFallback() {
        LlmConfig.DeepSeekConfig cfg = llmConfig.getDeepseek();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    private StreamingChatModel buildBaiStreamNoFallback() {
        LlmConfig.BaiLianConfig cfg = llmConfig.getBailian();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    private StreamingChatModel buildOpenStreamNoFallback() {
        LlmConfig.OpenAiConfig cfg = llmConfig.getOpenai();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    // ===================== 对外同步构建（异常统一切DeepSeek，不再循环 =====================
    private ChatModel buildDeepSync() {
        try {
            return buildDeepSyncNoFallback();
        } catch (Exception e) {
            log.error("DeepSeek同步模型创建失败", e);
            return buildDeepSyncNoFallback();
        }
    }

    private ChatModel buildBaiSync() {
        try {
            return buildBaiSyncNoFallback();
        } catch (Exception e) {
            log.error("百炼同步模型创建失败，降级DeepSeek", e);
            return buildDeepSyncNoFallback();
        }
    }

    private ChatModel buildOpenSync() {
        try {
            return buildOpenSyncNoFallback();
        } catch (Exception e) {
            log.error("OpenAI同步模型创建失败，降级DeepSeek", e);
            return buildDeepSyncNoFallback();
        }
    }

    // ===================== 对外流式构建 =====================
    private StreamingChatModel buildDeepStream() {
        try {
            return buildDeepStreamNoFallback();
        } catch (Exception e) {
            log.error("DeepSeek流式模型创建失败", e);
            return buildDeepStreamNoFallback();
        }
    }

    private StreamingChatModel buildBaiStream() {
        try {
            return buildBaiStreamNoFallback();
        } catch (Exception e) {
            log.error("百炼流式模型创建失败，降级DeepSeek", e);
            return buildDeepStreamNoFallback();
        }
    }

    private StreamingChatModel buildOpenStream() {
        try {
            return buildOpenStreamNoFallback();
        } catch (Exception e) {
            log.error("OpenAI流式模型创建失败，降级DeepSeek", e);
            return buildDeepStreamNoFallback();
        }
    }
}