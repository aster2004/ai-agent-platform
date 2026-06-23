package com.ai.agentplatform.module.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * TODO D13系统优化阶段实现异步后台生成，当前仅占位
 */
@Data
@Schema(description = "异步代码生成请求（预留功能）")
public class CodeGenAsyncRequest {
    @NotBlank(message = "生成需求不能为空")
    @Schema(description = "代码需求", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prompt;

    @NotBlank(message = "异步回调地址不能为空")
    @Schema(description = "生成完成回调接口地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String callbackUrl;

    @Schema(description = "所属应用ID")
    private Long appId;

    @Schema(description = "选用模型")
    private String modelName;
}