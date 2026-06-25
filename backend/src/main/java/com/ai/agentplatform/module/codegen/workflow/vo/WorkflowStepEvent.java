package com.ai.agentplatform.module.codegen.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepEvent {

    private String type;
    private String step;
    private String label;
    private String message;
    private Object data;
}
