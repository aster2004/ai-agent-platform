package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话历史读取工具：从成员6 ChatMemoryService（Redis）加载 session 多轮消息
 *
 * <p>上下文窗口控制：最多取最近 20 条消息，总长度不超 4000 字符，
 * 避免超过大模型 token 上限导致生成截断或拒绝。</p>
 */
@Slf4j
@Component
public class ChatHistoryHelper {

    /** 单次加载最大消息条数（与 Token 预算匹配） */
    private static final int MAX_HISTORY_MESSAGES = 20;
    /** 拼接后历史文本最大字符数（约等于 ~1000 tokens） */
    private static final int MAX_HISTORY_CHARS = 4000;

    @Resource
    private ChatMemoryService chatMemoryService;

    /**
     * 从 Redis 加载会话多轮消息，自动做长度截断
     *
     * @param sessionId 会话 ID，null 则返回空列表
     * @return 格式为 ["user: xxx", "ai: xxx", ...] 的字符串列表，最近消息在前
     */
    public List<String> loadChatHistory(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<String> rawHistory = chatMemoryService.getContextMessagesAsText(sessionId);
        if (rawHistory.isEmpty()) {
            return Collections.emptyList();
        }

        // 从最新消息开始保留，总字符数不超过 MAX_HISTORY_CHARS
        List<String> trimmed = new ArrayList<>();
        int totalChars = 0;
        for (int i = rawHistory.size() - 1; i >= 0 && trimmed.size() < MAX_HISTORY_MESSAGES; i--) {
            String msg = rawHistory.get(i);
            int msgLen = msg.length();
            if (totalChars + msgLen > MAX_HISTORY_CHARS && !trimmed.isEmpty()) {
                break; // 再追加会超上限，停止
            }
            trimmed.add(0, msg); // 保持时间顺序（旧→新）
            totalChars += msgLen;
        }

        log.debug("加载会话 {} Redis 记忆: 原始{}条 → 截断{}条, 总字符{}",
                sessionId, rawHistory.size(), trimmed.size(), totalChars);
        return trimmed;
    }
}
