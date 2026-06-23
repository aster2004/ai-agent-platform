package com.ai.agentplatform.module.codegen.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 对话历史读取工具
 * TODO D11集成：对接成员6对话模块，从Redis读取session多轮消息
 */
@Slf4j
@Component
public class ChatHistoryHelper {

    /**
     * Mock：返回空对话历史列表
     */
    public List<String> loadChatHistory(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        log.info("[Mock加载会话{}历史消息，暂无数据]", sessionId);
        return Collections.emptyList();
    }
}