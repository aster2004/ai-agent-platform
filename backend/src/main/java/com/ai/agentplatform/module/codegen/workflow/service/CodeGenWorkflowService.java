package com.ai.agentplatform.module.codegen.workflow.service;

import com.ai.agentplatform.common.util.SecurityUtils;
import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.service.helper.AppSyncHelper;
import com.ai.agentplatform.module.codegen.service.helper.ChatHistoryHelper;
import com.ai.agentplatform.module.codegen.service.helper.ChatMemoryContext;
import com.ai.agentplatform.module.codegen.service.helper.CodegenMemoryService;
import com.ai.agentplatform.module.codegen.service.helper.SessionSummaryService;
import com.ai.agentplatform.module.codegen.service.tool.PromptBuilder;
import com.ai.agentplatform.module.codegen.support.CodeGenRequestContext;
import com.ai.agentplatform.module.codegen.workflow.dto.ContinueWorkflowRequest;
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
    private final WorkflowRecordPersistence recordPersistence;
    private final AppSyncHelper appSyncHelper;
    private final ChatHistoryHelper chatHelper;
    private final PromptBuilder promptBuilder;
    private final CodegenMemoryService codegenMemoryService;
    private final SessionSummaryService sessionSummaryService;
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
        WorkflowState finalState = runGraph(fullGraph, initState(buildWorkflowPrompt(request)), step -> { });
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
    public SseEmitter executeContinueStream(Long generateId, ContinueWorkflowRequest request) {
        String fallbackPrd = request != null ? request.getPrdContent() : null;
        String fallbackSummary = request != null ? request.getSummary() : null;
        return runContinueStream(generateId, fallbackPrd, fallbackSummary);
    }

    public WorkflowResultVO updatePrd(Long generateId, UpdatePrdRequest request) {
        CodeGenerate record = loadRecord(generateId);
        PendingState pending = resolvePendingState(record, request.getPrdContent(), null);
        pending.setPrd(request.getPrdContent());
        persistAwaitState(record, pending, record.getDuration());
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
        final String authorization = CodeGenRequestContext.captureAuthorization();

        Thread.startVirtualThread(() -> CodeGenRequestContext.runWithAuthorization(authorization, () -> {
            try {
                sendTask(emitter, tasks, "skill_call", "技能调用 deepresearch", "深度分析用户需求");
                WorkflowState finalState = runGraph(analyzeGraph, initState(buildWorkflowPrompt(request)), step -> {
                    sendStepEvent(emitter, step);
                    if (step == WorkflowStep.PRD) {
                        try {
                            sendTask(emitter, tasks, "command", "执行命令 mkdir -p docs", "创建文档目录");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, false);

                sendTask(emitter, tasks, "save_file", "保存内容 需求文档.md", "prd.md");
                PendingState pending = new PendingState();
                pending.setPhase(PHASE_AWAIT);
                pending.setSummary(finalState.summary());
                pending.setPrd(finalState.prd());
                pending.setTasks(tasks);
                if (!hasText(pending.getPrd())) {
                    throw new IllegalStateException("PRD 生成结果为空，请重试");
                }
                persistAwaitState(record, pending, (int) (System.currentTimeMillis() - start));

                codegenMemoryService.rememberWorkflowAnalysisAsync(
                        request.getSessionId(), request.getPrompt(), finalState.summary(), null);
                sessionSummaryService.refreshAfterWorkflowAnalysisAsync(request.getSessionId());

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
        }));
        return emitter;
    }

    private SseEmitter runContinueStream(Long generateId, String fallbackPrd, String fallbackSummary) {
        SseEmitter emitter = new SseEmitter(600_000L);
        long start = System.currentTimeMillis();
        final String authorization = CodeGenRequestContext.captureAuthorization();

        Thread.startVirtualThread(() -> CodeGenRequestContext.runWithAuthorization(authorization, () -> {
            CodeGenerate record = null;
            try {
                record = loadRecord(generateId);
                PendingState pending = resolvePendingState(record, fallbackPrd, fallbackSummary);

                Map<String, Object> init = new HashMap<>();
                init.put(WorkflowState.PROMPT_KEY, buildWorkflowPrompt(
                        record.getPrompt(), record.getSessionId(), record.getAppId()));
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
        }));
        return emitter;
    }

    private SseEmitter runStream(WorkflowRequest request, CompiledGraph<WorkflowState> graph, boolean finalize) {
        SseEmitter emitter = new SseEmitter(600_000L);
        long start = System.currentTimeMillis();
        CodeGenerate record = createRecord(request);
        final String authorization = CodeGenRequestContext.captureAuthorization();

        Thread.startVirtualThread(() -> CodeGenRequestContext.runWithAuthorization(authorization, () -> {
            try {
                WorkflowState finalState = runGraph(graph, initState(buildWorkflowPrompt(request)), step -> sendStepEvent(emitter, step));
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
        }));
        return emitter;
    }

    private void handleStreamError(SseEmitter emitter, CodeGenerate record, Exception e) {
        log.error("工作流流式执行失败", e);
        if (record != null) {
            markFailed(record, e.getMessage());
        }
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

    /** 与 quick codegen 一致：从 Redis 加载 session 多轮记忆并拼入 Prompt */
    private String buildWorkflowPrompt(WorkflowRequest request) {
        return buildWorkflowPrompt(request.getPrompt(), request.getSessionId(), request.getAppId());
    }

    private String buildWorkflowPrompt(String userPrompt, Long sessionId, Long appId) {
        ChatMemoryContext memoryContext = chatHelper.loadMemoryContext(sessionId, appId);
        log.debug("工作流加载会话 {} 记忆: 概要={}, 近期{}条",
                sessionId,
                memoryContext.hasSessionSummary() ? "有" : "无",
                memoryContext.recentDialogue().size());

        CodeGenRequest codeGenRequest = new CodeGenRequest();
        codeGenRequest.setPrompt(userPrompt);
        codeGenRequest.setAppId(appId != null ? appId : 1L);
        codeGenRequest.setSessionId(sessionId);
        codeGenRequest.setGenerateType("WORKFLOW");
        return promptBuilder.buildPrompt(codeGenRequest, memoryContext);
    }

    private WorkflowState runGraph(CompiledGraph<WorkflowState> graph, Map<String, Object> init,
                                   Consumer<WorkflowStep> stepListener) {
        return runGraph(graph, init, stepListener, true);
    }

    private WorkflowState runGraph(CompiledGraph<WorkflowState> graph, Map<String, Object> init,
                                   Consumer<WorkflowStep> stepListener, boolean notifyDone) {
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
        if (notifyDone) {
            stepListener.accept(WorkflowStep.DONE);
        }
        return finalState;
    }

    private CodeGenerate createRecord(WorkflowRequest request) {
        CodeGenerate record = new CodeGenerate();
        record.setUserId(SecurityUtils.getCurrentUserId());
        record.setAppId(request.getAppId() != null ? request.getAppId() : CodeGenConstant.UNASSIGNED_APP_ID);
        record.setSessionId(request.getSessionId());
        record.setPrompt(request.getPrompt());
        record.setGenerateType("WORKFLOW");
        record.setGenerateStatus(0);
        record.setModelName(modelName);
        record.setWorkflowStep(WorkflowStep.ANALYZE.getCode());
        return recordPersistence.save(record);
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
        recordPersistence.save(record);

        if (success && !files.isEmpty()) {
            Long appId = record.getAppId();
            if (appId == null || appId <= 0) {
                appId = appSyncHelper.resolveOrCreateApp(null, null, record.getPrompt());
                record.setAppId(appId != null ? appId : CodeGenConstant.UNASSIGNED_APP_ID);
            }
            if (appId != null && appId > 0) {
                appSyncHelper.syncCodeFilesToApp(appId, files);
            }
            writeWorkflowCodegenMemory(record, state);
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

    private void persistAwaitState(CodeGenerate record, PendingState pending, Integer durationMs) {
        try {
            String json = objectMapper.writeValueAsString(pending);
            recordPersistence.persistAwaitState(record, PENDING_PREFIX + json, durationMs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存 PRD 状态失败", e);
        }
    }

    /**
     * 解析待确认工作流状态。DB 缺失时，可用请求体中的 PRD 内容重建（如从 SSE 事件恢复）。
     */
    private PendingState resolvePendingState(CodeGenerate record, String fallbackPrd, String fallbackSummary) {
        PendingState fromDb = tryReadPendingState(record);
        if (fromDb != null && hasText(fromDb.getPrd())) {
            return fromDb;
        }

        String prd = hasText(fallbackPrd)
                ? fallbackPrd
                : (fromDb != null ? fromDb.getPrd() : null);
        String summary = fromDb != null && hasText(fromDb.getSummary())
                ? fromDb.getSummary()
                : (hasText(fallbackSummary) ? fallbackSummary : record.getPrompt());

        if (!hasText(prd)) {
            if (WorkflowStep.PRD_READY.getCode().equals(record.getWorkflowStep())) {
                throw new IllegalStateException("PRD 数据缺失，请重新执行深度分析");
            }
            throw new IllegalStateException("记录中无 PRD 数据，请先执行深度分析");
        }

        PendingState pending = fromDb != null ? fromDb : new PendingState();
        pending.setPhase(PHASE_AWAIT);
        pending.setSummary(summary);
        pending.setPrd(prd);
        if (pending.getTasks() == null) {
            pending.setTasks(new ArrayList<>());
        }
        persistAwaitState(record, pending, record.getDuration());
        return pending;
    }

    private PendingState tryReadPendingState(CodeGenerate record) {
        String raw = record.getCodeContent();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith(PENDING_PREFIX)) {
            try {
                return parsePendingJson(trimmed.substring(PENDING_PREFIX.length()));
            } catch (IllegalStateException e) {
                log.warn("解析 PENDING 状态失败 generateId={}: {}", record.getId(), e.getMessage());
                return null;
            }
        }
        if (trimmed.startsWith("[")) {
            throw new IllegalStateException("该记录已完成生成，请重新发起深度分析");
        }
        if (trimmed.startsWith("{")) {
            try {
                return parsePendingJson(trimmed);
            } catch (IllegalStateException e) {
                log.warn("解析 JSON 状态失败 generateId={}: {}", record.getId(), e.getMessage());
                return null;
            }
        }

        PendingState pending = new PendingState();
        pending.setPhase(PHASE_AWAIT);
        pending.setPrd(raw);
        pending.setSummary(record.getPrompt());
        return pending;
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void markFailed(CodeGenerate record, String error) {
        record.setGenerateStatus(2);
        record.setErrorMsg(error);
        record.setWorkflowStep(WorkflowStep.DONE.getCode());
        recordPersistence.save(record);
    }

    private String resolveGenerateType(String strategy) {
        if ("VUE".equals(strategy) || "WORKFLOW".equals(strategy)) {
            return "VUE";
        }
        return strategy != null && !strategy.isBlank() ? strategy : "WORKFLOW";
    }

    private void writeWorkflowCodegenMemory(CodeGenerate record, WorkflowState state) {
        if (record.getSessionId() == null) {
            return;
        }
        try {
            String codeContent = objectMapper.writeValueAsString(state.codeFiles());
            codegenMemoryService.rememberCodegenResultAsync(
                    record.getSessionId(),
                    record.getPrompt(),
                    resolveGenerateType(state.strategy()),
                    codeContent);
        } catch (JsonProcessingException e) {
            log.warn("工作流写生成记忆失败 sessionId={}", record.getSessionId(), e);
        }
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
        @com.fasterxml.jackson.annotation.JsonProperty("prdContent")
        @com.fasterxml.jackson.annotation.JsonAlias({"prd"})
        private String prd;
        private List<WorkflowTaskVO> tasks = new ArrayList<>();
    }
}
