package com.ai.agentplatform.module.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryMessageVO {

    private String role;
    private String content;
}
