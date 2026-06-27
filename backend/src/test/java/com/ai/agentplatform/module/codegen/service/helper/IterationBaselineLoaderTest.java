package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.codegen.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.mapper.CodeGenerateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IterationBaselineLoaderTest {

    @Mock
    private AppSyncHelper appSyncHelper;

    @Mock
    private CodeGenerateMapper codeGenerateMapper;

    @Mock
    private com.ai.agentplatform.module.chat.repository.ChatSessionRepository chatSessionRepository;

    @Mock
    private com.ai.agentplatform.module.chat.repository.ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private IterationBaselineLoader loader;

    @Test
    void prefersAppCodeWhenAvailable() {
        when(appSyncHelper.resolveAppIdForSession(10L, 5L)).thenReturn(5L);
        when(appSyncHelper.getAppCode(5L)).thenReturn("<html><body>日历</body></html>");

        String baseline = loader.load(10L, 5L);

        assertNotNull(baseline);
        assertTrue(baseline.contains("日历"));
    }

    @Test
    void fallsBackToCodeGenerateRecord() {
        when(appSyncHelper.resolveAppIdForSession(10L, null)).thenReturn(null);
        CodeGenerate record = new CodeGenerate();
        record.setId(99L);
        record.setCodeContent("[{\"path\":\"index.html\",\"content\":\"<html>cal</html>\"}]");
        when(codeGenerateMapper.selectLatestSuccessBySessionId(10L)).thenReturn(record);

        String baseline = loader.load(10L, null);

        assertNotNull(baseline);
        assertTrue(baseline.contains("index.html"));
    }
}
