package com.ai.agentplatform.module.chat.service;

import com.ai.agentplatform.module.chat.vo.ChatMemoryMessageVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_MESSAGES = 50;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void addMessage(Long sessionId, String messageType, String content) {
        if (!"user".equals(messageType) && !"ai".equals(messageType)) {
            return;
        }
        String key = buildKey(sessionId);
        try {
            String payload = objectMapper.writeValueAsString(Map.of("role", messageType, "content", content));
            stringRedisTemplate.opsForList().rightPush(key, payload);
            stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
            stringRedisTemplate.expire(key, TTL);
        } catch (JsonProcessingException e) {
            log.error("Redis 记忆写入失败, sessionId={}", sessionId, e);
            throw new IllegalStateException("Redis 记忆写入失败");
        }
    }

    /**
     * 读取 Redis 多轮上下文，供 AI 基础生成（成员4 ChatHistoryHelper）及调试接口使用。
     */
    public List<ChatMemoryMessageVO> getContextMessages(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        String key = buildKey(sessionId);
        List<String> rawList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        return rawList.stream()
                .map(this::parseMemoryPayload)
                .filter(vo -> vo != null && StringUtils.hasText(vo.getContent()))
                .collect(Collectors.toList());
    }

    /**
     * 转为成员4 PromptBuilder 可用的字符串列表，格式：user: xxx / ai: xxx
     */
    public List<String> getContextMessagesAsText(Long sessionId) {
        return getContextMessages(sessionId).stream()
                .map(vo -> vo.getRole() + ": " + vo.getContent())
                .collect(Collectors.toList());
    }

    public void deleteMemory(Long sessionId) {
        stringRedisTemplate.delete(buildKey(sessionId));
    }

    private ChatMemoryMessageVO parseMemoryPayload(String json) {
        try {
            Map<String, String> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new ChatMemoryMessageVO(map.get("role"), map.get("content"));
        } catch (JsonProcessingException e) {
            log.warn("Redis 记忆解析失败, json={}", json, e);
            return null;
        }
    }

    private String buildKey(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
