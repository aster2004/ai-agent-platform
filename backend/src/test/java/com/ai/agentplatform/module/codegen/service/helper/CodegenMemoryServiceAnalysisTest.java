package com.ai.agentplatform.module.codegen.service.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodegenMemoryServiceAnalysisTest {

    private final CodegenMemoryService service = new CodegenMemoryService(null, null);

    @Test
    void buildAnalysisMemoryFormat() {
        String memory = service.buildAnalysisMemory(
                "做一个待办 App",
                "用户需要任务列表与增删功能",
                "VUE");

        assertTrue(memory.startsWith("[分析]"));
        assertTrue(memory.contains("做一个待办 App"));
        assertTrue(memory.contains("VUE"));
        assertTrue(memory.contains("PRD 已生成"));
    }
}
