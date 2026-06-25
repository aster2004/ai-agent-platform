package com.ai.agentplatform.module.chat.repository;

import com.ai.agentplatform.module.chat.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Page<ChatSession> findByUserIdOrderByLastMessageTimeDescCreateTimeDesc(Long userId, Pageable pageable);

    Page<ChatSession> findByUserIdAndAppIdOrderByLastMessageTimeDescCreateTimeDesc(
            Long userId, Long appId, Pageable pageable);
}
