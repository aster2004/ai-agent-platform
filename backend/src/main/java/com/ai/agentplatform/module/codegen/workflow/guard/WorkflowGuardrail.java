package com.ai.agentplatform.module.codegen.workflow.guard;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class WorkflowGuardrail {

    private static final List<String> SENSITIVE_WORDS = List.of(
            "password", "api-key", "secret", "token", "hack", "crack"
    );

    public String checkPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "需求描述不能为空";
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (String word : SENSITIVE_WORDS) {
            if (lower.contains(word)) {
                return "需求包含敏感词，请修改后重试";
            }
        }
        return null;
    }

    public boolean validateFiles(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return false;
        }
        for (String content : contents) {
            if (content == null || content.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
