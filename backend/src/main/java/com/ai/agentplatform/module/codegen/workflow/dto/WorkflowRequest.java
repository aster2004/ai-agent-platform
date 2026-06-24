package com.ai.agentplatform.module.codegen.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowRequest {

    @NotBlank(message = "需求描述不能为空")
    private String prompt;

    /** 并行开发期默认 appId=1 */
    private Long appId = 1L;

    private Long sessionId;
}
