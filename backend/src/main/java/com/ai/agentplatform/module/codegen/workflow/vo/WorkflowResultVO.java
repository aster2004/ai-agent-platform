package com.ai.agentplatform.module.codegen.workflow.vo;

import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkflowResultVO {

    private Long generateId;
    private String summary;
    private String strategy;
    private String generateType;
    private boolean validated;
    private String error;
    private List<CodeFile> codeFiles;
    private Integer durationMs;
}
