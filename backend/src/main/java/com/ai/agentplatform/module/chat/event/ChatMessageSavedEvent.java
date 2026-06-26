package com.ai.agentplatform.module.chat.event;

/**
 * 对话消息保存后发布，供 SessionSummaryService 等异步处理。
 */
public record ChatMessageSavedEvent(Long sessionId, int messageCount) {
}
