package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.guard.WorkflowGuardrail;
import com.ai.agentplatform.module.codegen.workflow.support.PrdTextSanitizer;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import org.springframework.util.StringUtils;
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
                你是需求分析助手。请用 1～2 句中文纯文字总结用户需求的核心功能与交互要点。
                严禁输出：HTML、CSS、JavaScript、Vue、代码块、markdown 代码围栏、标签或任何程序代码。
                用户需求：
                %s
                """.formatted(prompt);

        String summary = PrdTextSanitizer.sanitizeSummary(chatModel.chat(analyzePrompt));
        if (!StringUtils.hasText(summary)) {
            summary = fallbackSummary(prompt);
        }
        log.info("[Workflow] 分析结果: {}", summary);

        return Map.of(
                WorkflowState.SUMMARY_KEY, summary,
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.PRD.getCode()
        );
    }

    private static String fallbackSummary(String prompt) {
        String p = prompt == null ? "" : prompt.trim().replaceAll("\\s+", " ");
        if (p.length() > 160) {
            p = p.substring(0, 160) + "…";
        }
        return StringUtils.hasText(p) ? p : "用户希望生成一个可交互的前端应用页面。";
    }
}
