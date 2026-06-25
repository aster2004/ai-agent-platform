package com.ai.agentplatform.module.codegen.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTaskVO {

    /** skill_call | command | save_file | read_file */
    private String type;
    private String label;
    private String detail;
}
