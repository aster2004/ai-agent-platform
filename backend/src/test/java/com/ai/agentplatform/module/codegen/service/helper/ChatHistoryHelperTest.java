package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * ChatHistoryHelper 上下文截断单元测试
 */
@ExtendWith(MockitoExtension.class)
class ChatHistoryHelperTest {

    @Mock
    private ChatMemoryService chatMemoryService;

    @InjectMocks
    private ChatHistoryHelper chatHistoryHelper;

    @BeforeEach
    void setUp() {
        // InjectMocks handles injection
    }

    @Test
    void shouldReturnEmptyListWhenSessionIdIsNull() {
        List<String> result = chatHistoryHelper.loadChatHistory(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenRedisHasNoData() {
        when(chatMemoryService.getContextMessagesAsText(100L))
                .thenReturn(Collections.emptyList());

        List<String> result = chatHistoryHelper.loadChatHistory(100L);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnAllMessagesWhenUnderLimit() {
        List<String> messages = List.of(
                "user: Hello",
                "ai: Hi there",
                "user: How are you?"
        );
        when(chatMemoryService.getContextMessagesAsText(1L)).thenReturn(messages);

        List<String> result = chatHistoryHelper.loadChatHistory(1L);
        assertEquals(3, result.size());
        assertEquals("user: Hello", result.get(0));
        assertEquals("ai: Hi there", result.get(1));
        assertEquals("user: How are you?", result.get(2));
    }

    @Test
    void shouldTruncateWhenExceedsMaxMessages() {
        // 生成 30 条消息（超过 MAX_HISTORY_MESSAGES=20）
        List<String> messages = IntStream.range(0, 30)
                .mapToObj(i -> "user: message " + i)
                .toList();
        when(chatMemoryService.getContextMessagesAsText(1L)).thenReturn(messages);

        List<String> result = chatHistoryHelper.loadChatHistory(1L);
        assertTrue(result.size() <= 20, "应该最多保留20条");
    }

    @Test
    void shouldTruncateWhenExceedsMaxChars() {
        // 生成超长消息，每条 300 字符 → 15 条就超过 4000 字符
        String longContent = "x".repeat(280); // 加上 "user: " ≈ 286 chars
        List<String> messages = IntStream.range(0, 30)
                .mapToObj(i -> "user: " + longContent + i)
                .toList();
        when(chatMemoryService.getContextMessagesAsText(1L)).thenReturn(messages);

        List<String> result = chatHistoryHelper.loadChatHistory(1L);
        assertTrue(result.size() < 30, "超长消息应该被截断");
        int totalChars = result.stream().mapToInt(String::length).sum();
        assertTrue(totalChars <= 4200, "总字符数应接近 4000 上限: 实际 " + totalChars);
    }

    @Test
    void shouldKeepRecentMessagesWhenTruncating() {
        List<String> messages = new ArrayList<>();
        messages.add("user: oldest message");
        for (int i = 1; i < 25; i++) {
            messages.add("user: message " + i);
        }
        messages.add("user: newest message");
        when(chatMemoryService.getContextMessagesAsText(1L)).thenReturn(messages);

        List<String> result = chatHistoryHelper.loadChatHistory(1L);
        // 最旧的消息可能在截断时被丢弃
        assertFalse(result.contains("user: oldest message"),
                "最旧消息应该被截断丢弃");
        // 最新的消息应该保留
        assertTrue(result.get(result.size() - 1).contains("newest"),
                "最新消息应该保留: " + result.get(result.size() - 1));
    }
}
