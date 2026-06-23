package com.ai.agentplatform.module.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

/**
 * TODO D13系统优化阶段实现批量生成功能，当前仅定义参数不实现业务
 */
@Data
@Schema(description = "批量代码生成请求（预留功能）")
public class CodeGenBatchRequest {
    @Schema(description = "多条需求列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> promptList;

    @Schema(description = "统一归属应用ID", example = "1")
    private Long appId;

    @Schema(description = "统一使用模型", example = "deepseek")
    private String modelName;
}