package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import com.ai.agentplatform.module.codegen.service.factory.LlmModelFactory;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * 代码生成完成后，异步写入 Redis 语义记忆（Layer 1）。
 */
@Slf4j
@Service
public class CodegenMemoryService {

    private static final int MAX_MEMORY_CHARS = 300;
    private static final int MAX_PROMPT_CHARS = 120;

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是代码生成平台的记忆助手。根据用户需求、生成类型和代码指纹，输出一条记忆摘要。
            要求：
            1. 使用中文，不超过150字
            2. 严格四行格式，不要输出代码块：
            [生成] 需求：「…」
                  类型：…
                  实现：…（功能/UI，不要贴代码）
                  文件：…
            3. 「实现」描述用户得到了什么功能或界面，不要列举行数
            """;

    private final ChatMemoryService chatMemoryService;
    private final LlmModelFactory llmModelFactory;

    @Resource(name = "codeGenStreamExecutor")
    private Executor memoryExecutor;

    public CodegenMemoryService(ChatMemoryService chatMemoryService, LlmModelFactory llmModelFactory) {
        this.chatMemoryService = chatMemoryService;
        this.llmModelFactory = llmModelFactory;
    }

    /**
     * 异步写入生成记忆；sessionId 为空时跳过。
     */
    public void rememberCodegenResultAsync(Long sessionId, String userPrompt,
                                           String generateType, String codeContent) {
        if (sessionId == null) {
            return;
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return;
        }
        memoryExecutor.execute(() -> rememberCodegenResult(sessionId, userPrompt, generateType, codeContent));
    }

    /**
     * Workflow 分析阶段直写 Redis（不调 LLM，复用 AnalyzeNode 产出的 summary）。
     */
    public void rememberWorkflowAnalysisAsync(Long sessionId, String userPrompt, String summary, String strategy) {
        if (sessionId == null || summary == null || summary.isBlank()) {
            return;
        }
        memoryExecutor.execute(() -> {
            String memory = buildAnalysisMemory(userPrompt, summary, strategy);
            chatMemoryService.addMessage(sessionId, "ai", truncate(memory, MAX_MEMORY_CHARS));
            log.info("已写入工作流分析记忆, sessionId={}", sessionId);
        });
    }

    String buildAnalysisMemory(String userPrompt, String summary, String strategy) {
        String summaryText = summary.length() > 200 ? summary.substring(0, 200) + "…" : summary;
        String strategyText = (strategy != null && !strategy.isBlank()) ? strategy : "待确认";
        String promptHint = (userPrompt != null && !userPrompt.isBlank())
                ? truncatePrompt(userPrompt)
                : "见近期 user 消息";
        return """
                [分析] 需求：「%s」
                      摘要：%s
                      策略：%s
                      状态：PRD 已生成，待用户确认
                """.formatted(promptHint, summaryText, strategyText).trim();
    }

    void rememberCodegenResult(Long sessionId, String userPrompt, String generateType, String codeContent) {
        String memory;
        try {
            memory = summarizeWithLlm(userPrompt, generateType, codeContent);
        } catch (Exception e) {
            log.warn("生成记忆摘要失败，使用 fallback, sessionId={}: {}", sessionId, e.getMessage());
            memory = buildFallbackMemory(userPrompt, generateType, codeContent);
        }
        chatMemoryService.addMessage(sessionId, "ai", truncate(memory, MAX_MEMORY_CHARS));
        log.info("已写入生成记忆, sessionId={}, chars={}", sessionId, memory.length());
    }

    private String summarizeWithLlm(String userPrompt, String generateType, String codeContent) {
        CodeFingerprintExtractor.Fingerprint fingerprint = CodeFingerprintExtractor.extract(codeContent);
        String typeLabel = CodeFingerprintExtractor.describeGenerateType(generateType);
        String fileNames = CodeFingerprintExtractor.joinFileNames(fingerprint.fileNames());

        String userMessage = """
                用户需求：%s
                生成类型：%s
                文件列表：%s
                主入口片段：
                %s
                """.formatted(
                truncatePrompt(userPrompt),
                typeLabel,
                fileNames,
                fingerprint.mainSnippet());

        ChatModel chatModel = llmModelFactory.getChatModel(null);
        String summary = chatModel.chat(SUMMARY_SYSTEM_PROMPT + "\n\n" + userMessage).trim();
        if (summary.isBlank()) {
            throw new IllegalStateException("摘要 LLM 返回空内容");
        }
        if (!summary.startsWith("[生成]")) {
            summary = normalizeSummary(summary, userPrompt, typeLabel, fileNames);
        }
        return summary;
    }

    String buildFallbackMemory(String userPrompt, String generateType, String codeContent) {
        CodeFingerprintExtractor.Fingerprint fingerprint = CodeFingerprintExtractor.extract(codeContent);
        String typeLabel = CodeFingerprintExtractor.describeGenerateType(generateType);
        String fileNames = CodeFingerprintExtractor.joinFileNames(fingerprint.fileNames());
        return """
                [生成] 需求：「%s」
                      类型：%s
                      实现：（摘要生成失败，请结合近期 user 消息理解）
                      文件：%s
                """.formatted(truncatePrompt(userPrompt), typeLabel, fileNames).trim();
    }

    private static String normalizeSummary(String summary, String userPrompt, String typeLabel, String fileNames) {
        return """
                [生成] 需求：「%s」
                      类型：%s
                      实现：%s
                      文件：%s
                """.formatted(truncatePrompt(userPrompt), typeLabel, summary, fileNames).trim();
    }

    private static String truncatePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.length() <= MAX_PROMPT_CHARS ? prompt : prompt.substring(0, MAX_PROMPT_CHARS) + "…";
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
