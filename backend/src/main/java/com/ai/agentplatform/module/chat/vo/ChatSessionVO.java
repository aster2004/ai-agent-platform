package com.ai.agentplatform.module.chat.vo;

import com.ai.agentplatform.module.chat.entity.ChatSession;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {

    private Long id;
    private Long appId;
    private String sessionTitle;
    private String lastMessagePreview;
    private Integer messageCount;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;

    public static ChatSessionVO from(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setAppId(session.getAppId());
        vo.setSessionTitle(session.getSessionTitle());
        vo.setLastMessagePreview(session.getLastMessagePreview());
        vo.setMessageCount(session.getMessageCount());
        vo.setLastMessageTime(session.getLastMessageTime());
        vo.setCreateTime(session.getCreateTime());
        return vo;
    }
}
