package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.entity.ChatSession;
import com.ai.agentplatform.module.chat.repository.ChatSessionRepository;
import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryHelperMemoryContextTest {

    @Mock
    private ChatMemoryService chatMemoryService;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @InjectMocks
    private ChatHistoryHelper chatHistoryHelper;

    @Test
    void loadMemoryContextCombinesSummaryAndRecentDialogue() {
        ChatSession session = new ChatSession();
        session.setId(13L);
        session.setMemorySummary("用户在做静态日历页，已支持淡绿色背景。");

        when(chatSessionRepository.findById(13L)).thenReturn(Optional.of(session));
        when(chatMemoryService.getContextMessagesAsText(13L)).thenReturn(List.of(
                "user: 请做一个日历",
                "ai: [生成] 类型：HTML 单页"
        ));

        ChatMemoryContext context = chatHistoryHelper.loadMemoryContext(13L);

        assertTrue(context.hasSessionSummary());
        assertEquals("用户在做静态日历页，已支持淡绿色背景。", context.sessionSummary());
        assertEquals(2, context.recentDialogue().size());
    }

    @Test
    void loadMemoryContextWorksWhenSummaryIsNull() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setMemorySummary(null);

        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatMemoryService.getContextMessagesAsText(1L)).thenReturn(List.of("user: hi"));

        ChatMemoryContext context = chatHistoryHelper.loadMemoryContext(1L);

        assertFalse(context.hasSessionSummary());
        assertEquals(1, context.recentDialogue().size());
    }
}
