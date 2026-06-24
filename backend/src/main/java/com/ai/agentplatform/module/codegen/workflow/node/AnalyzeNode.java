package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.guard.WorkflowGuardrail;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class AnalyzeNode implements NodeAction<WorkflowState> {

    private final ChatModel chatModel;
    private final WorkflowGuardrail guardrail;

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("[Workflow] 节点: ANALYZE 开始");
        String prompt = state.prompt();

        String guardError = guardrail.checkPrompt(prompt);
        if (guardError != null) {
            return Map.of(
                    WorkflowState.ERROR_KEY, guardError,
                    WorkflowState.CURRENT_STEP_KEY, WorkflowStep.DONE.getCode()
            );
        }

        String analyzePrompt = """
                你是需求分析助手。请用 1-2 句话总结以下用户需求的核心功能与技术要点，不要输出代码：
                %s
                """.formatted(prompt);

        String summary = chatModel.chat(analyzePrompt);
        log.info("[Workflow] 分析结果: {}", summary);

        return Map.of(
                WorkflowState.SUMMARY_KEY, summary.trim(),
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.STRATEGY.getCode()
        );
    }
}
