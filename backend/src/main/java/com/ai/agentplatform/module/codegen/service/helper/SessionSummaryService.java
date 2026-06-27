package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.entity.ChatSession;
import com.ai.agentplatform.module.chat.event.ChatMessageSavedEvent;
import com.ai.agentplatform.module.chat.repository.ChatSessionRepository;
import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import com.ai.agentplatform.module.codegen.service.factory.LlmModelFactory;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Layer 2 长期记忆：增量合并 Redis 摘要，写入 chat_session.memory_summary。
 */
@Slf4j
@Service
public class SessionSummaryService {

    private static final int REFRESH_MESSAGE_INTERVAL = 8;
    private static final int REDIS_SIZE_THRESHOLD = 15;
    private static final int MAX_SUMMARY_CHARS = 300;

    private static final String MERGE_SYSTEM_PROMPT = """
            你是会话记忆助手。根据【旧概要】和【新增对话摘要】输出更新后的会话概要。
            要求：中文，不超过250字；保留项目类型、已实现功能、用户偏好、待办改动；不要输出代码或文件名列表。
            """;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryService chatMemoryService;
    private final LlmModelFactory llmModelFactory;

    @Resource(name = "codeGenStreamExecutor")
    private Executor summaryExecutor;

    public SessionSummaryService(ChatSessionRepository chatSessionRepository,
                                 ChatMemoryService chatMemoryService,
                                 LlmModelFactory llmModelFactory) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMemoryService = chatMemoryService;
        this.llmModelFactory = llmModelFactory;
    }

    @EventListener
    public void onChatMessageSaved(ChatMessageSavedEvent event) {
        refreshIfNeededAsync(event.sessionId(), event.messageCount());
    }

    public void refreshIfNeededAsync(Long sessionId, int messageCount) {
        if (sessionId == null) {
            return;
        }
        summaryExecutor.execute(() -> refreshIfNeeded(sessionId, messageCount, false));
    }

    /** Workflow 分析完成后可选立即刷新 Layer 2。 */
    public void refreshAfterWorkflowAnalysisAsync(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        summaryExecutor.execute(() -> {
            ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                return;
            }
            refreshIfNeeded(sessionId, session.getMessageCount(), true);
        });
    }

    @Transactional
    void refreshIfNeeded(Long sessionId, int messageCount, boolean force) {
        ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }

        int baseline = session.getSummaryMessageCount() != null ? session.getSummaryMessageCount() : 0;
        List<String> recent = chatMemoryService.getContextMessagesAsText(sessionId);
        if (recent.isEmpty()) {
            return;
        }

        boolean intervalReached = messageCount - baseline >= REFRESH_MESSAGE_INTERVAL;
        boolean redisOverflow = recent.size() >= REDIS_SIZE_THRESHOLD;
        if (!force && !intervalReached && !redisOverflow) {
            return;
        }

        String newSummary;
        try {
            newSummary = mergeWithLlm(session.getMemorySummary(), recent);
        } catch (Exception e) {
            log.warn("会话概要合并失败，使用 fallback, sessionId={}: {}", sessionId, e.getMessage());
            newSummary = buildFallbackSummary(session.getMemorySummary(), recent);
        }

        session.setMemorySummary(truncate(newSummary, MAX_SUMMARY_CHARS));
        session.setSummaryMessageCount(messageCount);
        session.setSummaryUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(session);
        log.info("已更新会话概要, sessionId={}, chars={}", sessionId, session.getMemorySummary().length());
    }

    private String mergeWithLlm(String oldSummary, List<String> recentDialogue) {
        String userMessage = """
                【旧概要】
                %s

                【新增对话摘要】
                %s
                """.formatted(
                oldSummary != null && !oldSummary.isBlank() ? oldSummary : "（无）",
                String.join("\n", recentDialogue));

        ChatModel chatModel = llmModelFactory.getChatModel(null);
        String merged = chatModel.chat(MERGE_SYSTEM_PROMPT + "\n\n" + userMessage).trim();
        if (merged.isBlank()) {
            throw new IllegalStateException("概要 LLM 返回空内容");
        }
        return merged;
    }

    String buildFallbackSummary(String oldSummary, List<String> recentDialogue) {
        String recent = String.join("；", recentDialogue);
        if (recent.length() > 180) {
            recent = recent.substring(0, 180) + "…";
        }
        if (oldSummary != null && !oldSummary.isBlank()) {
            return oldSummary + " " + recent;
        }
        return recent;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
