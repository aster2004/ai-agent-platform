package com.ai.agentplatform.module.codegen.service.helper;

import java.util.Collections;
import java.util.List;

/**
 * 拼 Prompt 用的记忆上下文：Layer 2 会话概要 + Layer 1 Redis 近期对话。
 */
public record ChatMemoryContext(String sessionSummary, List<String> recentDialogue, String iterationBaseline) {

    public ChatMemoryContext {
        recentDialogue = recentDialogue == null ? List.of() : List.copyOf(recentDialogue);
    }

  /** 兼容仅 Layer1/2、无迭代基线的构造 */
    public ChatMemoryContext(String sessionSummary, List<String> recentDialogue) {
        this(sessionSummary, recentDialogue, null);
    }

    public static ChatMemoryContext empty() {
        return new ChatMemoryContext(null, Collections.emptyList(), null);
    }

    public boolean hasSessionSummary() {
        return sessionSummary != null && !sessionSummary.isBlank();
    }

    public boolean hasRecentDialogue() {
        return !recentDialogue.isEmpty();
    }

    public boolean hasIterationBaseline() {
        return iterationBaseline != null && !iterationBaseline.isBlank();
    }
}
