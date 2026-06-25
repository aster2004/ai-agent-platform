package com.ai.agentplatform.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatSaveRequest {
    private Long sessionId;
    private Long appId;
    @NotBlank(message = "消息类型不能为空")
    private String messageType;
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
