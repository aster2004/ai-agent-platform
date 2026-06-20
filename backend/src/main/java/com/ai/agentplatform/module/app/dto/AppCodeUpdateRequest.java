package com.ai.agentplatform.module.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppCodeUpdateRequest {

    @NotBlank(message = "代码内容不能为空")
    private String codeContent;
}
