package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.entity.ChatSession;
import com.ai.agentplatform.module.chat.repository.ChatSessionRepository;
import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 对话历史读取：Layer 2（MySQL memory_summary）+ Layer 1（Redis 短期记忆）。
 */
@Slf4j
@Component
public class ChatHistoryHelper {

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_HISTORY_CHARS = 4000;

    @Resource
    private ChatMemoryService chatMemoryService;

    @Resource
    private ChatSessionRepository chatSessionRepository;

    @Resource
    private IterationBaselineLoader iterationBaselineLoader;

    /**
     * 加载 Layer 2 + Layer 1 + 迭代代码基线，供 PromptBuilder 组装。
     */
    public ChatMemoryContext loadMemoryContext(Long sessionId) {
        return loadMemoryContext(sessionId, null);
    }

    /**
     * 加载 Layer 2 + Layer 1 + 迭代代码基线，供 PromptBuilder 组装。
     *
     * @param requestAppId 生成请求中的 appId，用于读取 app.app_code
     */
    public ChatMemoryContext loadMemoryContext(Long sessionId, Long requestAppId) {
        if (sessionId == null) {
            return ChatMemoryContext.empty();
        }
        String summary = loadSessionSummary(sessionId);
        List<String> recent = loadChatHistory(sessionId);
        String baseline = iterationBaselineLoader.load(sessionId, requestAppId);
        if (summary != null) {
            log.debug("加载会话 {} 长期记忆: {} 字", sessionId, summary.length());
        }
        if (baseline != null) {
            log.debug("加载会话 {} 迭代基线: {} 字", sessionId, baseline.length());
        }
        return new ChatMemoryContext(summary, recent, baseline);
    }

    /**
     * 从 Redis 加载 Layer 1 近期对话，自动做长度截断。
     */
    public List<String> loadChatHistory(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<String> rawHistory = chatMemoryService.getContextMessagesAsText(sessionId);
        if (rawHistory.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> trimmed = new ArrayList<>();
        int totalChars = 0;
        for (int i = rawHistory.size() - 1; i >= 0 && trimmed.size() < MAX_HISTORY_MESSAGES; i--) {
            String msg = rawHistory.get(i);
            int msgLen = msg.length();
            if (totalChars + msgLen > MAX_HISTORY_CHARS && !trimmed.isEmpty()) {
                break;
            }
            trimmed.add(0, msg);
            totalChars += msgLen;
        }

        log.debug("加载会话 {} Redis 记忆: 原始{}条 → 保留{}条, 总字符{}",
                sessionId, rawHistory.size(), trimmed.size(), totalChars);
        return trimmed;
    }

    private String loadSessionSummary(Long sessionId) {
        Optional<ChatSession> session = chatSessionRepository.findById(sessionId);
        return session.map(ChatSession::getMemorySummary)
                .filter(summary -> summary != null && !summary.isBlank())
                .orElse(null);
    }
}
