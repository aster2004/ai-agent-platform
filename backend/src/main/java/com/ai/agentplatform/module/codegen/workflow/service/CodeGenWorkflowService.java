package com.ai.agentplatform.module.codegen.workflow.service;

import com.ai.agentplatform.common.util.SecurityUtils;
import com.ai.agentplatform.module.codegen.service.helper.AppSyncHelper;
import com.ai.agentplatform.module.codegen.workflow.dto.UpdatePrdRequest;
import com.ai.agentplatform.module.codegen.workflow.dto.WorkflowRequest;
import com.ai.agentplatform.module.codegen.workflow.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.workflow.node.AnalyzeNode;
import com.ai.agentplatform.module.codegen.workflow.node.GenerateNode;
import com.ai.agentplatform.module.codegen.workflow.node.PrdNode;
import com.ai.agentplatform.module.codegen.workflow.node.StrategyNode;
import com.ai.agentplatform.module.codegen.workflow.node.ValidateNode;
import com.ai.agentplatform.module.codegen.workflow.repository.CodeGenerateRepository;
import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import com.ai.agentplatform.module.codegen.workflow.vo.WorkflowResultVO;
import com.ai.agentplatform.module.codegen.workflow.vo.WorkflowStepEvent;
import com.ai.agentplatform.module.codegen.workflow.support.StandalonePreviewBuilder;
import com.ai.agentplatform.module.codegen.workflow.vo.WorkflowTaskVO;
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
import java.util.ArrayList;
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

    private static final String PHASE_AWAIT = "await_confirm";
    private static final String PHASE_DONE = "done";
    private static final String PENDING_PREFIX = "@WORKFLOW_PENDING@";

    private final AnalyzeNode analyzeNode;
    private final PrdNode prdNode;
    private final StrategyNode strategyNode;
    private final GenerateNode generateNode;
    private final ValidateNode validateNode;
    private final CodeGenerateRepository codeGenerateRepository;
    private final AppSyncHelper appSyncHelper;
    private final ObjectMapper objectMapper;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}")
    private String modelName;

    private CompiledGraph<WorkflowState> analyzeGraph;
    private CompiledGraph<WorkflowState> generateGraph;
    private CompiledGraph<WorkflowState> fullGraph;

    @PostConstruct
    void initGraph() throws GraphStateException {
        analyzeGraph = new StateGraph<>(WorkflowState.SCHEMA, WorkflowState::new)
                .addNode(WorkflowStep.ANALYZE.getCode(), node_async(analyzeNode))
                .addNode(WorkflowStep.PRD.getCode(), node_async(prdNode))
                .addEdge(START, WorkflowStep.ANALYZE.getCode())
                .addEdge(WorkflowStep.ANALYZE.getCode(), WorkflowStep.PRD.getCode())
                .addEdge(WorkflowStep.PRD.getCode(), END)
                .compile();

        generateGraph = new StateGraph<>(WorkflowState.SCHEMA, WorkflowState::new)
                .addNode(WorkflowStep.STRATEGY.getCode(), node_async(strategyNode))
                .addNode(WorkflowStep.GENERATE.getCode(), node_async(generateNode))
                .addNode(WorkflowStep.VALIDATE.getCode(), node_async(validateNode))
                .addEdge(START, WorkflowStep.STRATEGY.getCode())
                .addEdge(WorkflowStep.STRATEGY.getCode(), WorkflowStep.GENERATE.getCode())
                .addEdge(WorkflowStep.GENERATE.getCode(), WorkflowStep.VALIDATE.getCode())
                .addEdge(WorkflowStep.VALIDATE.getCode(), END)
                .compile();

        fullGraph = new StateGraph<>(WorkflowState.SCHEMA, WorkflowState::new)
                .addNode(WorkflowStep.ANALYZE.getCode(), node_async(analyzeNode))
                .addNode(WorkflowStep.PRD.getCode(), node_async(prdNode))
                .addNode(WorkflowStep.STRATEGY.getCode(), node_async(strategyNode))
                .addNode(WorkflowStep.GENERATE.getCode(), node_async(generateNode))
                .addNode(WorkflowStep.VALIDATE.getCode(), node_async(validateNode))
                .addEdge(START, WorkflowStep.ANALYZE.getCode())
                .addEdge(WorkflowStep.ANALYZE.getCode(), WorkflowStep.PRD.getCode())
                .addEdge(WorkflowStep.PRD.getCode(), WorkflowStep.STRATEGY.getCode())
                .addEdge(WorkflowStep.STRATEGY.getCode(), WorkflowStep.GENERATE.getCode())
                .addEdge(WorkflowStep.GENERATE.getCode(), WorkflowStep.VALIDATE.getCode())
                .addEdge(WorkflowStep.VALIDATE.getCode(), END)
                .compile();

        log.info("LangGraph4j 工作流编译完成（分析图 + 生成图 + 全量图）");
    }

    public WorkflowResultVO execute(WorkflowRequest request) {
        long start = System.currentTimeMillis();
        CodeGenerate record = createRecord(request);
        WorkflowState finalState = runGraph(fullGraph, initState(request.getPrompt()), step -> { });
        return finalizeRecord(record, finalState, start);
    }

    public SseEmitter executeStream(WorkflowRequest request) {
        return runStream(request, fullGraph, true);
    }

    /** 深度分析阶段：分析需求 + 生成 PRD，等待用户确认 */
    public SseEmitter executeAnalyzeStream(WorkflowRequest request) {
        return runAnalyzeStream(request);
    }

    /** 用户确认 PRD 后继续生成应用 */
    public SseEmitter executeContinueStream(Long generateId) {
        return runContinueStream(generateId);
    }

    public WorkflowResultVO updatePrd(Long generateId, UpdatePrdRequest request) {
        CodeGenerate record = loadRecord(generateId);
        PendingState pending = readPendingState(record);
        pending.setPrd(request.getPrdContent());
        savePendingState(record, pending);
        return WorkflowResultVO.builder()
                .generateId(record.getId())
                .phase(PHASE_AWAIT)
                .summary(pending.getSummary())
                .prdContent(pending.getPrd())
                .tasks(pending.getTasks())
                .build();
    }

    private SseEmitter runAnalyzeStream(WorkflowRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L);
        long start = System.currentTimeMillis();
        CodeGenerate record = createRecord(request);
        List<WorkflowTaskVO> tasks = new ArrayList<>();

        Thread.startVirtualThread(() -> {
            try {
                sendTask(emitter, tasks, "skill_call", "技能调用 deepresearch", "深度分析用户需求");
                WorkflowState finalState = runGraph(analyzeGraph, initState(request.getPrompt()), step -> {
                    sendStepEvent(emitter, step);
                    if (step == WorkflowStep.PRD) {
                        try {
                            sendTask(emitter, tasks, "command", "执行命令 mkdir -p docs", "创建文档目录");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

                sendTask(emitter, tasks, "save_file", "保存内容 需求文档.md", "prd.md");
                PendingState pending = new PendingState();
                pending.setPhase(PHASE_AWAIT);
                pending.setSummary(finalState.summary());
                pending.setPrd(finalState.prd());
                pending.setTasks(tasks);
                savePendingState(record, pending);

                record.setWorkflowStep(WorkflowStep.PRD_READY.getCode());
                record.setGenerateStatus(0);
                record.setDuration((int) (System.currentTimeMillis() - start));
                codeGenerateRepository.save(record);

                WorkflowResultVO result = WorkflowResultVO.builder()
                        .generateId(record.getId())
                        .phase(PHASE_AWAIT)
                        .summary(finalState.summary())
                        .prdContent(finalState.prd())
                        .tasks(tasks)
                        .durationMs((int) (System.currentTimeMillis() - start))
                        .build();

                sendEvent(emitter, WorkflowStepEvent.builder()
                        .type("prd_ready")
                        .step(WorkflowStep.PRD_READY.getCode())
                        .label(WorkflowStep.PRD_READY.getLabel())
                        .message("已根据您的需求生成产品文档")
                        .data(result)
                        .build());
                emitter.complete();
            } catch (Exception e) {
                handleStreamError(emitter, record, e);
            }
        });
        return emitter;
    }

    private SseEmitter runContinueStream(Long generateId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        long start = System.currentTimeMillis();
        CodeGenerate record = loadRecord(generateId);
        PendingState pending = readPendingState(record);

        Thread.startVirtualThread(() -> {
            try {
                Map<String, Object> init = new HashMap<>();
                init.put(WorkflowState.PROMPT_KEY, record.getPrompt());
                init.put(WorkflowState.SUMMARY_KEY, pending.getSummary());
                init.put(WorkflowState.PRD_KEY, pending.getPrd());
                init.put(WorkflowState.CURRENT_STEP_KEY, WorkflowStep.STRATEGY.getCode());

                sendStepEvent(emitter, WorkflowStep.STRATEGY);
                WorkflowState finalState = runGraph(generateGraph, init, step -> sendStepEvent(emitter, step));
                WorkflowResultVO result = finalizeRecord(record, finalState, start);
                result.setPhase(PHASE_DONE);
                result.setPrdContent(pending.getPrd());
                result.setTasks(pending.getTasks());

                sendEvent(emitter, WorkflowStepEvent.builder()
                        .type("done")
                        .step(WorkflowStep.DONE.getCode())
                        .label(WorkflowStep.DONE.getLabel())
                        .message("应用生成完成")
                        .data(result)
                        .build());
                emitter.complete();
            } catch (Exception e) {
                handleStreamError(emitter, record, e);
            }
        });
        return emitter;
    }

    private SseEmitter runStream(WorkflowRequest request, CompiledGraph<WorkflowState> graph, boolean finalize) {
        SseEmitter emitter = new SseEmitter(600_000L);
        long start = System.currentTimeMillis();
        CodeGenerate record = createRecord(request);

        Thread.startVirtualThread(() -> {
            try {
                WorkflowState finalState = runGraph(graph, initState(request.getPrompt()), step -> sendStepEvent(emitter, step));
                if (finalize) {
                    WorkflowResultVO result = finalizeRecord(record, finalState, start);
                    result.setPrdContent(finalState.prd());
                    sendEvent(emitter, WorkflowStepEvent.builder()
                            .type("done")
                            .step(WorkflowStep.DONE.getCode())
                            .label(WorkflowStep.DONE.getLabel())
                            .message("工作流执行完成")
                            .data(result)
                            .build());
                }
                emitter.complete();
            } catch (Exception e) {
                handleStreamError(emitter, record, e);
            }
        });
        return emitter;
    }

    private void handleStreamError(SseEmitter emitter, CodeGenerate record, Exception e) {
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
            emitter.completeWithError(e);
        }
    }

    private Map<String, Object> initState(String prompt) {
        Map<String, Object> init = new HashMap<>();
        init.put(WorkflowState.PROMPT_KEY, prompt);
        init.put(WorkflowState.CURRENT_STEP_KEY, WorkflowStep.ANALYZE.getCode());
        return init;
    }

    private WorkflowState runGraph(CompiledGraph<WorkflowState> graph, Map<String, Object> init,
                                   Consumer<WorkflowStep> stepListener) {
        WorkflowState finalState = null;
        WorkflowStep lastNotified = null;

        for (var nodeOutput : graph.stream(init)) {
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
        record.setUserId(SecurityUtils.getCurrentUserId());
        record.setAppId(request.getAppId() != null ? request.getAppId() : 1L);
        record.setSessionId(request.getSessionId());
        record.setPrompt(request.getPrompt());
        record.setGenerateType("WORKFLOW");
        record.setGenerateStatus(0);
        record.setModelName(modelName);
        record.setWorkflowStep(WorkflowStep.ANALYZE.getCode());
        return codeGenerateRepository.save(record);
    }

    private CodeGenerate loadRecord(Long generateId) {
        return codeGenerateRepository.findById(generateId)
                .orElseThrow(() -> new IllegalArgumentException("生成记录不存在: " + generateId));
    }

    private WorkflowResultVO finalizeRecord(CodeGenerate record, WorkflowState state, long startMs) {
        List<CodeFile> files = new ArrayList<>(state.codeFiles());
        StandalonePreviewBuilder.appendPreviewFile(files);
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

        if (success && !files.isEmpty()) {
            appSyncHelper.syncCodeFilesToApp(record.getAppId(), files);
        }

        return WorkflowResultVO.builder()
                .generateId(record.getId())
                .phase(PHASE_DONE)
                .summary(state.summary())
                .prdContent(state.prd())
                .strategy(strategy)
                .generateType(resolveGenerateType(strategy))
                .validated(validated)
                .error(error)
                .codeFiles(files)
                .durationMs(duration)
                .build();
    }

    private void savePendingState(CodeGenerate record, PendingState pending) {
        try {
            String json = objectMapper.writeValueAsString(pending);
            record.setCodeContent(PENDING_PREFIX + json);
            codeGenerateRepository.save(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存 PRD 状态失败", e);
        }
    }

    private PendingState readPendingState(CodeGenerate record) {
        String raw = record.getCodeContent();
        if (raw == null || raw.isBlank()) {
            return recoverPendingFromRecord(record);
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith(PENDING_PREFIX)) {
            return parsePendingJson(trimmed.substring(PENDING_PREFIX.length()));
        }
        if (trimmed.startsWith("[")) {
            throw new IllegalStateException("该记录已完成生成，请重新发起深度分析");
        }
        if (!trimmed.startsWith("{")) {
            PendingState pending = new PendingState();
            pending.setPhase(PHASE_AWAIT);
            pending.setPrd(raw);
            pending.setSummary(record.getPrompt());
            return pending;
        }
        return parsePendingJson(trimmed);
    }

    private PendingState parsePendingJson(String json) {
        try {
            return objectMapper.readValue(json, PendingState.class);
        } catch (JsonProcessingException e) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(json.substring(start, end + 1), PendingState.class);
                } catch (JsonProcessingException ignored) {
                    // fall through
                }
            }
            throw new IllegalStateException("解析 PRD 状态失败，请重新执行深度分析", e);
        }
    }

    private PendingState recoverPendingFromRecord(CodeGenerate record) {
        if (WorkflowStep.PRD_READY.getCode().equals(record.getWorkflowStep())) {
            PendingState pending = new PendingState();
            pending.setPhase(PHASE_AWAIT);
            pending.setSummary(record.getPrompt());
            throw new IllegalStateException("PRD 数据缺失，请重新执行深度分析");
        }
        throw new IllegalStateException("记录中无 PRD 数据，请先执行深度分析");
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

    private void sendTask(SseEmitter emitter, List<WorkflowTaskVO> tasks,
                          String type, String label, String detail) throws IOException {
        WorkflowTaskVO task = WorkflowTaskVO.builder().type(type).label(label).detail(detail).build();
        tasks.add(task);
        sendEvent(emitter, WorkflowStepEvent.builder()
                .type("task")
                .message(label)
                .data(task)
                .build());
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

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class PendingState {
        private String phase = PHASE_AWAIT;
        private String summary;
        @com.fasterxml.jackson.annotation.JsonAlias({"prdContent"})
        private String prd;
        private List<WorkflowTaskVO> tasks = new ArrayList<>();
    }
}
