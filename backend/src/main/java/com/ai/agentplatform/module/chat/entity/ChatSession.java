package com.ai.agentplatform.module.chat.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "app_id")
    private Long appId;

    @Column(name = "session_title", length = 100)
    private String sessionTitle;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
