package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.guard.WorkflowGuardrail;
import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class ValidateNode implements NodeAction<WorkflowState> {

    private final WorkflowGuardrail guardrail;

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("[Workflow] 节点: VALIDATE 开始");
        List<CodeFile> files = state.codeFiles();
        List<String> contents = files.stream().map(CodeFile::getContent).toList();

        if (files.isEmpty()) {
            return Map.of(
                    WorkflowState.VALIDATED_KEY, false,
                    WorkflowState.ERROR_KEY, "未生成任何代码文件",
                    WorkflowState.CURRENT_STEP_KEY, WorkflowStep.DONE.getCode()
            );
        }

        boolean valid = guardrail.validateFiles(contents);
        for (CodeFile file : files) {
            if (file.getPath() == null || file.getPath().isBlank()) {
                valid = false;
                break;
            }
            if (file.getPath().endsWith(".html") && !file.getContent().contains("<html")) {
                valid = false;
            }
            if (file.getPath().endsWith(".vue") && !file.getContent().contains("<template")) {
                valid = false;
            }
        }

        log.info("[Workflow] 校验结果: {}", valid);
        return Map.of(
                WorkflowState.VALIDATED_KEY, valid,
                WorkflowState.ERROR_KEY, valid ? "" : "代码校验未通过，请调整需求后重试",
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.DONE.getCode()
        );
    }
}
