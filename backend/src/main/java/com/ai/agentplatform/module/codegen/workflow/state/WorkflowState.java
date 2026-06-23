package com.ai.agentplatform.module.codegen.workflow.state;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkflowState extends AgentState {

    public static final String PROMPT_KEY = "prompt";
    public static final String SUMMARY_KEY = "summary";
    public static final String STRATEGY_KEY = "strategy";
    public static final String CODE_FILES_KEY = "codeFiles";
    public static final String CURRENT_STEP_KEY = "currentStep";
    public static final String VALIDATED_KEY = "validated";
    public static final String ERROR_KEY = "error";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            PROMPT_KEY, Channels.base(() -> ""),
            SUMMARY_KEY, Channels.base(() -> ""),
            STRATEGY_KEY, Channels.base(() -> ""),
            CODE_FILES_KEY, Channels.base(ArrayList::new),
            CURRENT_STEP_KEY, Channels.base(() -> WorkflowStep.ANALYZE.getCode()),
            VALIDATED_KEY, Channels.base(() -> false),
            ERROR_KEY, Channels.base(() -> "")
    );

    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    public String prompt() {
        return value(PROMPT_KEY).map(Object::toString).orElse("");
    }

    public String summary() {
        return value(SUMMARY_KEY).map(Object::toString).orElse("");
    }

    public String strategy() {
        return value(STRATEGY_KEY).map(Object::toString).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<CodeFile> codeFiles() {
        return value(CODE_FILES_KEY)
                .map(v -> (List<CodeFile>) v)
                .orElseGet(ArrayList::new);
    }

    public String currentStep() {
        return value(CURRENT_STEP_KEY).map(Object::toString).orElse(WorkflowStep.ANALYZE.getCode());
    }

    public boolean validated() {
        return value(VALIDATED_KEY).map(v -> (Boolean) v).orElse(false);
    }

    public String error() {
        return value(ERROR_KEY).map(Object::toString).orElse("");
    }
}
