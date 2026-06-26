package com.ai.agentplatform.module.codegen.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowRequest {

    @NotBlank(message = "需求描述不能为空")
    private String prompt;

    /** 已有应用 ID；不传则在生成成功后自动创建 */
    private Long appId;

    /** 应用名称；不传则从 prompt 前 20 字推导 */
    private String appName;

    private Long sessionId;
}
