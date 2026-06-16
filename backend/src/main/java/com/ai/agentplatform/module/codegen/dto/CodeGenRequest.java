package com.ai.agentplatform.module.codegen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CodeGenRequest {

    @NotBlank(message = "需求描述不能为空")
    private String prompt;

    private Long appId;
}
