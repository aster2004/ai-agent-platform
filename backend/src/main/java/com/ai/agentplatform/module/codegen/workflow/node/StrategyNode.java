package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class StrategyNode implements NodeAction<WorkflowState> {

    private static final String SYSTEM_STRATEGY_PROMPT = """
            根据需求摘要，只输出一个策略类型，不要解释。
            可选值：HTML、VUE、MULTI_FILE、WORKFLOW
            规则：
            - 提到 Vue、组件、前端工程 → VUE
            - 提到多页面、多个 html 文件 → MULTI_FILE
            - 简单单页网页 → HTML
            - 复杂流程、多步骤 → WORKFLOW
            """;

    private final ChatModel chatModel;

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("[Workflow] 节点: STRATEGY 开始");
        if (!state.error().isBlank()) {
            return Map.of(WorkflowState.CURRENT_STEP_KEY, WorkflowStep.VALIDATE.getCode());
        }
        String strategy = resolveStrategy(state.prompt(), state.summary());
        log.info("[Workflow] 选定策略: {}", strategy);

        return Map.of(
                WorkflowState.STRATEGY_KEY, strategy,
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.GENERATE.getCode()
        );
    }

    private String resolveStrategy(String prompt, String summary) {
        String text = (prompt + " " + summary).toLowerCase(Locale.ROOT);
        if (text.contains("vue") || text.contains("组件") || text.contains("vite")) {
            return "VUE";
        }
        if (text.contains("多文件") || text.contains("multi") || text.contains("多个页面")) {
            return "MULTI_FILE";
        }
        if (text.contains("工作流") || text.contains("workflow") || text.contains("流程")) {
            return "WORKFLOW";
        }

        String aiStrategy = chatModel.chat(SYSTEM_STRATEGY_PROMPT + "\n\n需求摘要：\n" + summary);
        String normalized = aiStrategy.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("VUE")) {
            return "VUE";
        }
        if (normalized.contains("MULTI_FILE")) {
            return "MULTI_FILE";
        }
        if (normalized.contains("WORKFLOW")) {
            return "WORKFLOW";
        }
        return "HTML";
    }
}
