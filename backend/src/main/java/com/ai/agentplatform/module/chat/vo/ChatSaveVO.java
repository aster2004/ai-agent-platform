package com.ai.agentplatform.module.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatSaveVO {

    private Long sessionId;
    private Long messageId;
}
