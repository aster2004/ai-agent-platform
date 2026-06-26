package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.service.ChatMemoryService;
import com.ai.agentplatform.module.codegen.service.factory.LlmModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CodegenMemoryServiceTest {

    @Mock
    private ChatMemoryService chatMemoryService;

    @Mock
    private LlmModelFactory llmModelFactory;

    @InjectMocks
    private CodegenMemoryService codegenMemoryService;

    @Test
    void fallbackMemoryWrittenWhenLlmUnavailable() {
        String html = "<!DOCTYPE html><html><head><title>Todo</title></head><body></body></html>";
        codegenMemoryService.rememberCodegenResult(1L, "做一个待办清单", "HTML", html);

        verify(chatMemoryService).addMessage(eq(1L), eq("ai"), org.mockito.ArgumentMatchers.argThat(content ->
                content.startsWith("[生成]")
                        && content.contains("做一个待办清单")
                        && content.contains("HTML 单页")
                        && content.contains("index.html")));
    }
}
