package com.ai.agentplatform.module.chat.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.chat.dto.ChatSaveRequest;
import com.ai.agentplatform.module.chat.dto.ChatSessionCreateRequest;
import com.ai.agentplatform.module.chat.entity.ChatMessage;
import com.ai.agentplatform.module.chat.entity.ChatSession;
import com.ai.agentplatform.module.chat.repository.ChatMessageRepository;
import com.ai.agentplatform.module.chat.repository.ChatSessionRepository;
import com.ai.agentplatform.module.chat.vo.ChatHistoryVO;
import com.ai.agentplatform.module.chat.vo.ChatMemoryMessageVO;
import com.ai.agentplatform.module.chat.vo.ChatMessageVO;
import com.ai.agentplatform.module.chat.vo.ChatSaveVO;
import com.ai.agentplatform.module.chat.vo.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TITLE_MAX_LEN = 20;
    private static final int PREVIEW_MAX_LEN = 50;
    private static final int NOT_DELETED = 0;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMemoryService chatMemoryService;

    @Transactional
    public ChatSaveVO saveMessage(ChatSaveRequest request, Long userId) {
        validateMessageType(request.getMessageType());

        ChatSession session = resolveSession(request, userId);
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setAppId(session.getAppId());
        message.setMessageType(request.getMessageType());
        message.setContent(request.getContent());
        message.setIsDeleted(NOT_DELETED);
        message = chatMessageRepository.save(message);

        updateSessionAfterMessage(session, request.getMessageType(), request.getContent());
        chatMemoryService.addMessage(session.getId(), request.getMessageType(), request.getContent());

        return new ChatSaveVO(session.getId(), message.getId());
    }

    public ChatHistoryVO getHistory(Long sessionId, Long userId, Long cursor, int size) {
        getOwnedSession(sessionId, userId);

        int pageSize = Math.min(Math.max(size, 1), 50);
        List<ChatMessage> messages = fetchMessages(sessionId, cursor, pageSize + 1);

        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(0, pageSize);
        }

        Collections.reverse(messages);

        ChatHistoryVO vo = new ChatHistoryVO();
        vo.setList(messages.stream().map(ChatMessageVO::from).toList());
        vo.setHasMore(hasMore);
        vo.setNextCursor(hasMore && !messages.isEmpty() ? messages.get(0).getId() : null);
        return vo;
    }

    @Transactional
    public ChatSessionVO createSession(ChatSessionCreateRequest request, Long userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAppId(request.getAppId());
        session.setMessageCount(0);
        return ChatSessionVO.from(chatSessionRepository.save(session));
    }

    public Page<ChatSessionVO> listSessions(Long userId, Long appId, int page, int size) {
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), 50);
        PageRequest pageRequest = PageRequest.of(pageIndex, pageSize);

        Page<ChatSession> sessions = appId != null
                ? chatSessionRepository.findByUserIdAndAppIdOrderByLastMessageTimeDescCreateTimeDesc(
                        userId, appId, pageRequest)
                : chatSessionRepository.findByUserIdOrderByLastMessageTimeDescCreateTimeDesc(
                        userId, pageRequest);

        return sessions.map(ChatSessionVO::from);
    }

    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("消息不存在"));
        ChatSession session = getOwnedSession(msg.getSessionId(), userId);
        msg.setIsDeleted(1);
        chatMessageRepository.save(msg);
    }

    @Transactional
    public void renameSession(Long sessionId, String title, Long userId) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        ChatSession session = getOwnedSession(sessionId, userId);
        session.setSessionTitle(title.trim());
        chatSessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = getOwnedSession(sessionId, userId);
        chatSessionRepository.delete(session);
        chatMemoryService.deleteMemory(sessionId);
    }

    public List<ChatMemoryMessageVO> getMemory(Long sessionId, Long userId) {
        getOwnedSession(sessionId, userId);
        return chatMemoryService.getContextMessages(sessionId);
    }

    private ChatSession resolveSession(ChatSaveRequest request, Long userId) {
        if (request.getSessionId() != null) {
            return getOwnedSession(request.getSessionId(), userId);
        }

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAppId(request.getAppId());
        session.setMessageCount(0);
        return chatSessionRepository.save(session);
    }

    private void updateSessionAfterMessage(ChatSession session, String messageType, String content) {
        session.setMessageCount(session.getMessageCount() + 1);
        session.setLastMessagePreview(truncate(content, PREVIEW_MAX_LEN));
        session.setLastMessageTime(LocalDateTime.now());

        if ("user".equals(messageType) && session.getMessageCount() == 1) {
            session.setSessionTitle(truncate(content, TITLE_MAX_LEN));
        }

        chatSessionRepository.save(session);
    }

    private List<ChatMessage> fetchMessages(Long sessionId, Long cursor, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        if (cursor == null) {
            return chatMessageRepository.findBySessionIdAndIsDeletedOrderByIdDesc(
                    sessionId, NOT_DELETED, pageRequest);
        }
        return chatMessageRepository.findBySessionIdAndIsDeletedAndIdLessThanOrderByIdDesc(
                sessionId, NOT_DELETED, cursor, pageRequest);
    }

    private ChatSession getOwnedSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return session;
    }

    private void validateMessageType(String messageType) {
        if (!"user".equals(messageType) && !"ai".equals(messageType)) {
            throw new BusinessException("消息类型只能是 user 或 ai");
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
