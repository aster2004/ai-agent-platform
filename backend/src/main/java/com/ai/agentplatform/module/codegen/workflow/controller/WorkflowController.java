package com.ai.agentplatform.module.codegen.workflow.controller;

import com.ai.agentplatform.common.result.Result;
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
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "同步执行 LangGraph 工作流")
    @PostMapping
    public Result<WorkflowResultVO> execute(@Valid @RequestBody WorkflowRequest request) {
        return Result.success(workflowService.execute(request));
    }

    @Operation(summary = "流式执行 LangGraph 工作流（SSE 推送节点进度）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeStream(@Valid @RequestBody WorkflowRequest request) {
        return workflowService.executeStream(request);
    }
}
