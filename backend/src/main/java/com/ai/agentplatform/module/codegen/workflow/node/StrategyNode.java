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
            根据用户需求，只输出一个策略类型，不要解释。
            可选值：HTML、VUE、MULTI_FILE、FULL_STACK、WORKFLOW
            规则：
            - 简单单页（登录页、计算器、Todo、表单、展示页）且用户未明确要求 Vue 工程 → HTML
            - 用户明确要求 Vue/Vite/组件化工程 → VUE
            - 多页面静态站点 → MULTI_FILE
            - 用户要求后端、API、数据库、Spring Boot → FULL_STACK
            - 复杂多步骤流程 → WORKFLOW
            """;

    private final ChatModel chatModel;

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("[Workflow] 节点: STRATEGY 开始");
        if (!state.error().isBlank()) {
            return Map.of(WorkflowState.CURRENT_STEP_KEY, WorkflowStep.VALIDATE.getCode());
        }
        String context = state.prd().isBlank() ? state.summary() : state.prd();
        String strategy = resolveStrategy(state.prompt(), context);
        log.info("[Workflow] 选定策略: {}", strategy);

        return Map.of(
                WorkflowState.STRATEGY_KEY, strategy,
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.GENERATE.getCode()
        );
    }

    private String resolveStrategy(String prompt, String summary) {
        String promptLower = prompt.toLowerCase(Locale.ROOT);
        String combined = (prompt + " " + summary).toLowerCase(Locale.ROOT);

        // 用户原始需求优先：明确要求 Vue 工程才走 VUE
        if (userWantsVueProject(promptLower)) {
            return "VUE";
        }

        // 用户要求后端 → 全栈（前端 index.html + 后端代码，预览最稳定）
        if (userWantsBackend(promptLower, combined)) {
            return "FULL_STACK";
        }

        // 简单前端单页：登录、注册、计算器、Todo 等 → HTML（iframe 可直接预览）
        if (isSimpleFrontendPage(promptLower)) {
            return "HTML";
        }

        if (promptLower.contains("多文件") || promptLower.contains("multi") || promptLower.contains("多个页面")) {
            return "MULTI_FILE";
        }
        if (combined.contains("工作流") || combined.contains("workflow") || combined.contains("流程")) {
            return "WORKFLOW";
        }

        String aiStrategy = chatModel.chat(SYSTEM_STRATEGY_PROMPT + "\n\n用户原始需求：\n" + prompt + "\n\n需求摘要：\n" + summary);
        String normalized = aiStrategy.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("FULL_STACK")) {
            return "FULL_STACK";
        }
        if (normalized.contains("VUE") && userWantsVueProject(promptLower)) {
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

    /** 用户明确要求 Vue/Vite 工程（而非泛泛的「前端代码」） */
    private boolean userWantsVueProject(String promptLower) {
        return promptLower.contains("vue") || promptLower.contains("vite")
                || promptLower.contains("vue3") || promptLower.contains("vue 3")
                || promptLower.contains("组件化") || promptLower.contains("前端工程")
                || promptLower.contains("前端项目");
    }

    private boolean userWantsBackend(String promptLower, String combined) {
        return promptLower.contains("后端") || promptLower.contains("backend")
                || promptLower.contains("spring") || promptLower.contains("api")
                || promptLower.contains("数据库") || promptLower.contains("服务端")
                || combined.contains("spring boot");
    }

    /** 典型可单 HTML 预览的简单前端页面 */
    private boolean isSimpleFrontendPage(String promptLower) {
        return promptLower.contains("登录") || promptLower.contains("login")
                || promptLower.contains("注册") || promptLower.contains("register")
                || promptLower.contains("计算器") || promptLower.contains("calculator")
                || promptLower.contains("todo") || promptLower.contains("待办")
                || promptLower.contains("简单") || promptLower.contains("单页")
                || promptLower.contains("静态页") || promptLower.contains("展示页")
                || (promptLower.contains("前端") && (promptLower.contains("页面") || promptLower.contains("代码")));
    }
}
