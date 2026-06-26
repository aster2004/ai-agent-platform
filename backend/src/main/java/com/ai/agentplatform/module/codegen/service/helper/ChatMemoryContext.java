package com.ai.agentplatform.module.codegen.service.helper;

import java.util.Collections;
import java.util.List;

/**
 * 拼 Prompt 用的记忆上下文：Layer 2 会话概要 + Layer 1 Redis 近期对话。
 */
public record ChatMemoryContext(String sessionSummary, List<String> recentDialogue) {

    public ChatMemoryContext {
        recentDialogue = recentDialogue == null ? List.of() : List.copyOf(recentDialogue);
    }

    public static ChatMemoryContext empty() {
        return new ChatMemoryContext(null, Collections.emptyList());
    }

    public boolean hasSessionSummary() {
        return sessionSummary != null && !sessionSummary.isBlank();
    }

    public boolean hasRecentDialogue() {
        return !recentDialogue.isEmpty();
    }
}
