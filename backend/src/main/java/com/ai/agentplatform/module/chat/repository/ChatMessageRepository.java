package com.ai.agentplatform.module.chat.repository;

import com.ai.agentplatform.module.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdAndIsDeletedOrderByIdDesc(Long sessionId, Integer isDeleted, Pageable pageable);

    List<ChatMessage> findBySessionIdAndIsDeletedAndIdLessThanOrderByIdDesc(
            Long sessionId, Integer isDeleted, Long id, Pageable pageable);
}
