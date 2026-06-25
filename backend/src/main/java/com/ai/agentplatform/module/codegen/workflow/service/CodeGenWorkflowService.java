package com.ai.agentplatform.module.codegen.workflow.service;

import com.ai.agentplatform.module.codegen.workflow.dto.WorkflowRequest;
import com.ai.agentplatform.module.codegen.workflow.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.workflow.node.AnalyzeNode;
import com.ai.agentplatform.module.codegen.workflow.node.GenerateNode;
import com.ai.agentplatform.module.codegen.workflow.node.StrategyNode;
import com.ai.agentplatform.module.codegen.workflow.node.ValidateNode;
import com.ai.agentplatform.module.codegen.workflow.repository.CodeGenerateRepository;
import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import com.ai.agentplatform.module.codegen.workflow.vo.WorkflowResultVO;
import com.ai.agentplatform.module.codegen.workflow.vo.WorkflowStepEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class CodeGenWorkflowService {

    private static final Long MOCK_USER_ID = 1L;

    private final AnalyzeNode analyzeNode;
    private final StrategyNode strategyNode;
    private final GenerateNode generateNode;
    private final ValidateNode validateNode;
    private final CodeGenerateRepository codeGenerateRepository;
    private final ObjectMapper objectMapper;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}")
    private String modelName;

    private CompiledGraph<WorkflowState> compiledGraph;

    @PostConstruct
    void initGraph() throws GraphStateException {
        StateGraph<WorkflowState> graph = new StateGraph<>(WorkflowState.SCHEMA, WorkflowState::new)
                .addNode(WorkflowStep.ANALYZE.getCode(), node_async(analyzeNode))
                .addNode(WorkflowStep.STRATEGY.getCode(), node_async(strategyNode))
                .addNode(WorkflowStep.GENERATE.getCode(), node_async(generateNode))
                .addNode(WorkflowStep.VALIDATE.getCode(), node_async(validateNode))
                .addEdge(START, WorkflowStep.ANALYZE.getCode())
                .addEdge(WorkflowStep.ANALYZE.getCode(), WorkflowStep.STRATEGY.getCode())
                .addEdge(WorkflowStep.STRATEGY.getCode(), WorkflowStep.GENERATE.getCode())
                .addEdge(WorkflowStep.GENERATE.getCode(), WorkflowStep.VALIDATE.getCode())
                .addEdge(WorkflowStep.VALIDATE.getCode(), END);

        compiledGraph = graph.compile();
        log.info("LangGraph4j 工作流编译完成");
    }

    public WorkflowResultVO execute(WorkflowRequest request) {
        long start = System.currentTimeMillis();
        CodeGenerate record = createRecord(request);
        WorkflowState finalState = runWorkflow(request.getPrompt(), step -> { });
        return finalizeRecord(record, finalState, start);
    }

    public SseEmitter executeStream(WorkflowRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L);
        long start = System.currentTimeMillis();
        CodeGenerate record = createRecord(request);

        Thread.startVirtualThread(() -> {
            try {
                WorkflowState finalState = runWorkflow(request.getPrompt(), step -> sendStepEvent(emitter, step));
                WorkflowResultVO result = finalizeRecord(record, finalState, start);
                sendEvent(emitter, WorkflowStepEvent.builder()
                        .type("done")
                        .step(WorkflowStep.DONE.getCode())
                        .label(WorkflowStep.DONE.getLabel())
                        .message("工作流执行完成")
                        .data(result)
                        .build());
                emitter.complete();
            } catch (Exception e) {
                log.error("工作流流式执行失败", e);
                markFailed(record, e.getMessage());
                try {
                    sendEvent(emitter, WorkflowStepEvent.builder()
                            .type("error")
                            .step(WorkflowStep.DONE.getCode())
                            .message(e.getMessage())
                            .build());
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    private WorkflowState runWorkflow(String prompt, Consumer<WorkflowStep> stepListener) {
        Map<String, Object> init = new HashMap<>();
        init.put(WorkflowState.PROMPT_KEY, prompt);
        init.put(WorkflowState.CURRENT_STEP_KEY, WorkflowStep.ANALYZE.getCode());

        WorkflowState finalState = null;
        WorkflowStep lastNotified = null;

        for (var nodeOutput : compiledGraph.stream(init)) {
            finalState = nodeOutput.state();
            if (finalState == null) {
                continue;
            }
            WorkflowStep current = WorkflowStep.fromCode(finalState.currentStep());
            if (lastNotified != current) {
                stepListener.accept(current);
                lastNotified = current;
            }
        }

        if (finalState == null) {
            throw new IllegalStateException("工作流未产生任何状态");
        }
        stepListener.accept(WorkflowStep.DONE);
        return finalState;
    }

    private CodeGenerate createRecord(WorkflowRequest request) {
        CodeGenerate record = new CodeGenerate();
        record.setUserId(MOCK_USER_ID);
        record.setAppId(request.getAppId() != null ? request.getAppId() : 1L);
        record.setSessionId(request.getSessionId());
        record.setPrompt(request.getPrompt());
        record.setGenerateType("WORKFLOW");
        record.setGenerateStatus(0);
        record.setModelName(modelName);
        record.setWorkflowStep(WorkflowStep.ANALYZE.getCode());
        return codeGenerateRepository.save(record);
    }

    private WorkflowResultVO finalizeRecord(CodeGenerate record, WorkflowState state, long startMs) {
        List<CodeFile> files = state.codeFiles();
        String strategy = state.strategy();
        boolean validated = state.validated();
        String error = state.error();
        int duration = (int) (System.currentTimeMillis() - startMs);

        try {
            record.setCodeContent(objectMapper.writeValueAsString(files));
        } catch (JsonProcessingException e) {
            record.setCodeContent("[]");
        }
        record.setGenerateType(resolveGenerateType(strategy));
        boolean success = validated && (error == null || error.isBlank());
        record.setGenerateStatus(success ? 1 : 2);
        record.setErrorMsg(error);
        record.setDuration(duration);
        record.setWorkflowStep(state.currentStep());
        codeGenerateRepository.save(record);

        return WorkflowResultVO.builder()
                .generateId(record.getId())
                .summary(state.summary())
                .strategy(strategy)
                .generateType(resolveGenerateType(strategy))
                .validated(validated)
                .error(error)
                .codeFiles(files)
                .durationMs(duration)
                .build();
    }

    private void markFailed(CodeGenerate record, String error) {
        record.setGenerateStatus(2);
        record.setErrorMsg(error);
        record.setWorkflowStep(WorkflowStep.DONE.getCode());
        codeGenerateRepository.save(record);
    }

    private String resolveGenerateType(String strategy) {
        if ("VUE".equals(strategy) || "WORKFLOW".equals(strategy)) {
            return "VUE";
        }
        return strategy != null && !strategy.isBlank() ? strategy : "WORKFLOW";
    }

    private void sendStepEvent(SseEmitter emitter, WorkflowStep step) {
        try {
            sendEvent(emitter, WorkflowStepEvent.builder()
                    .type("step")
                    .step(step.getCode())
                    .label(step.getLabel())
                    .message("正在执行: " + step.getLabel())
                    .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendEvent(SseEmitter emitter, WorkflowStepEvent event) throws IOException {
        emitter.send(SseEmitter.event().name("workflow").data(objectMapper.writeValueAsString(event)));
    }
}
