package com.ai.agentplatform.module.codegen.workflow.dto;

import lombok.Data;

@Data
public class ContinueWorkflowRequest {

    /** 阶段一 SSE 返回的 PRD，用于 DB 尚未落库时的兜底 */
    private String prdContent;

    /** 需求摘要兜底 */
    private String summary;
}
