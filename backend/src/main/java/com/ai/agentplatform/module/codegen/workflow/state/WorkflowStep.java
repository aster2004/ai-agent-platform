package com.ai.agentplatform.module.codegen.workflow.state;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkflowStep {

    ANALYZE("analyze", "分析需求"),
    STRATEGY("strategy", "选择策略"),
    GENERATE("generate", "生成代码"),
    VALIDATE("validate", "校验结果"),
    DONE("done", "完成");

    private final String code;
    private final String label;

    public static WorkflowStep fromCode(String code) {
        if (code == null) {
            return ANALYZE;
        }
        for (WorkflowStep step : values()) {
            if (step.code.equalsIgnoreCase(code)) {
                return step;
            }
        }
        return ANALYZE;
    }
}
