package com.ai.agentplatform.module.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "单次代码生成请求参数DTO")
public class CodeGenRequest {
    @NotBlank(message = "代码生成需求prompt不能为空")
    @Schema(description = "用户需求描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "写一个Vue3+Element Plus用户管理页面")
    private String prompt;

    @Positive(message = "应用ID必须为正整数")
    @Schema(description = "所属应用ID，不传默认Mock=1", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Long appId;

    @Positive(message = "会话ID必须为正整数")
    @Schema(description = "对话会话ID，空则无多轮上下文", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long sessionId;

    @Pattern(regexp = "^(HTML|VUE|MULTI_FILE|WORKFLOW|GENERAL)$", message = "生成类型仅支持：HTML/VUE/MULTI_FILE/WORKFLOW/GENERAL")
    @Schema(description = "代码生成类型", example = "VUE")
    private String generateType;

    //@Pattern(regexp = "^(deepseek|openai|bailian)$", message = "模型仅支持deepseek/openai/bailian")
    @Schema(description = "选用大模型", example = "deepseek")
    private String modelName;

    @DecimalMin(value = "0.0", message = "温度系数最小0.0")
    @DecimalMax(value = "1.0", message = "温度系数最大1.0")
    @Schema(description = "模型创造性系数", example = "0.7")
    private BigDecimal temperature;

    @Positive(message = "最大Token必须是正数")
    @Max(value = 5000, message = "最大Token不能超过5000")
    @Schema(description = "模型最大输出Token限制", example = "3000")
    private Integer maxTokens;
}