package com.ai.agentplatform.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRenameRequest {

    @NotBlank(message = "标题不能为空")
    private String title;
}
