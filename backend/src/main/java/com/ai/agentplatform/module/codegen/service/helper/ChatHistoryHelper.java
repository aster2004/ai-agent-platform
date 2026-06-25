package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 对话历史读取工具：从成员6 ChatMemoryService（Redis）加载 session 多轮消息
 */
@Slf4j
@Component
public class ChatHistoryHelper {

    @Resource
    private ChatMemoryService chatMemoryService;

    public List<String> loadChatHistory(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<String> history = chatMemoryService.getContextMessagesAsText(sessionId);
        log.debug("加载会话 {} Redis 记忆 {} 条", sessionId, history.size());
        return history;
    }
}
