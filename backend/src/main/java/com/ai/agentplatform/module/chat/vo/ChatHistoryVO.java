package com.ai.agentplatform.module.chat.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatHistoryVO {

    private List<ChatMessageVO> list;
    private Long nextCursor;
    private Boolean hasMore;
}
