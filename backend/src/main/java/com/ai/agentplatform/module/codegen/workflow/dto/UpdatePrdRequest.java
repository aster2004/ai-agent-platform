package com.ai.agentplatform.module.codegen.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePrdRequest {

    @NotBlank(message = "需求文档内容不能为空")
    private String prdContent;
}
