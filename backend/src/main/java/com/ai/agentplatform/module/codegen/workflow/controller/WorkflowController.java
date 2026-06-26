package com.ai.agentplatform.module.codegen.workflow.controller;

import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.codegen.workflow.dto.ContinueWorkflowRequest;
import com.ai.agentplatform.module.codegen.workflow.dto.UpdatePrdRequest;
import com.ai.agentplatform.module.codegen.workflow.dto.WorkflowRequest;
import com.ai.agentplatform.module.codegen.workflow.service.CodeGenWorkflowService;
import com.ai.agentplatform.module.codegen.workflow.vo.WorkflowResultVO;
import dev.langchain4j.model.chat.ChatModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI 工作流生成")
@RestController
@RequestMapping("/api/codegen/workflow")
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class WorkflowController {

    private final CodeGenWorkflowService workflowService;

    @Operation(summary = "同步执行 LangGraph 工作流（全量）")
    @PostMapping
    public Result<WorkflowResultVO> execute(@Valid @RequestBody WorkflowRequest request) {
        return Result.success(workflowService.execute(request));
    }

    // 使用标准 SSE 类型，避免 Vite/Node 代理解析 text/event-stream;charset=UTF-8 报 Invalid header token
    private static final String SSE_PRODUCES = MediaType.TEXT_EVENT_STREAM_VALUE;

    @Operation(summary = "流式执行 LangGraph 工作流（全量）")
    @PostMapping(value = "/stream", produces = SSE_PRODUCES)
    public SseEmitter executeStream(@Valid @RequestBody WorkflowRequest request) {
        return workflowService.executeStream(request);
    }

    @Operation(summary = "深度分析：生成需求文档（阶段一）")
    @PostMapping(value = "/analyze/stream", produces = SSE_PRODUCES)
    public SseEmitter analyzeStream(@Valid @RequestBody WorkflowRequest request) {
        return workflowService.executeAnalyzeStream(request);
    }

    @Operation(summary = "更新需求文档内容")
    @PutMapping("/{generateId}/prd")
    public Result<WorkflowResultVO> updatePrd(@PathVariable Long generateId,
                                              @Valid @RequestBody UpdatePrdRequest request) {
        return Result.success(workflowService.updatePrd(generateId, request));
    }

    @Operation(summary = "确认需求文档后生成应用（阶段二）")
    @PostMapping(value = "/{generateId}/continue/stream", produces = SSE_PRODUCES)
    public SseEmitter continueStream(@PathVariable Long generateId,
                                     @RequestBody(required = false) ContinueWorkflowRequest request) {
        return workflowService.executeContinueStream(generateId, request);
    }
}