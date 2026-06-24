package com.ai.agentplatform.module.chat.vo;

import com.ai.agentplatform.module.chat.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {

    private Long id;
    private Long sessionId;
    private String messageType;
    private String content;
    private LocalDateTime createTime;

    public static ChatMessageVO from(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
