package com.ai.agentplatform.module.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "代码生成分页统一返回VO，全局分页规范")
public class CodeGenPageVO {
    @Schema(description = "总记录数", example = "35")
    private Long total;

    @Schema(description = "当前页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;

    @Schema(description = "当前页数据列表")
    private List<CodeGenVO> list;
}