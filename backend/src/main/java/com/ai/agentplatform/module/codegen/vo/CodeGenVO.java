package com.ai.agentplatform.module.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "代码生成单条记录返回VO")
public class CodeGenVO {
    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "用户输入需求", example = "生成Vue登录页面")
    private String prompt;

    @Schema(description = "生成完整代码")
    private String codeContent;

    @Schema(description = "模型名称", example = "deepseek")
    private String modelName;

    @Schema(description = "生成类型", example = "VUE", allowableValues = {"HTML","VUE","MULTI_FILE","WORKFLOW"})
    private String generateType;

    @Schema(description = "消耗Token数", example = "1200")
    private Integer costTokens;

    @Schema(description = "生成状态 0生成中/1成功/2失败", example = "1")
    private Integer generateStatus;

    @Schema(description = "错误信息，成功为空")
    private String errorMsg;

    @Schema(description = "生成耗时ms", example = "1500")
    private Integer duration;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "工作流节点标识")
    private String workflowStep;
}