package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.entity.ChatSession;
import com.ai.agentplatform.module.chat.repository.ChatSessionRepository;
import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import com.ai.agentplatform.module.codegen.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.mapper.CodeGenerateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AppSyncHelper 多文件提取 + API 同步单元测试
 */
@ExtendWith(MockitoExtension.class)
class AppSyncHelperTest {

    @Mock
    private RestTemplate codeGenRestTemplate;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private CodeGenerateMapper codeGenerateMapper;

    @InjectMocks
    private AppSyncHelper appSyncHelper;

    // ==================== createApp ====================

    @Test
    void shouldCreateAppAndReturnId() {
        String respJson = """
                {"code":200,"data":{"id":42,"appName":"测试应用"}}""";
        when(codeGenRestTemplate.postForObject(eq("/api/app"), any(), eq(String.class)))
                .thenReturn(respJson);

        Long appId = appSyncHelper.createApp("测试应用", "描述");
        assertEquals(42L, appId);
    }

    @Test
    void shouldReturnNullWhenCreateAppFails() {
        when(codeGenRestTemplate.postForObject(eq("/api/app"), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertNull(appSyncHelper.createApp("测试应用", null));
    }

    @Test
    void shouldDeriveAppNameFromPrompt() {
        assertEquals("写一个Vue3用户管理页面", AppSyncHelper.deriveAppName(null, "写一个Vue3用户管理页面"));
        assertEquals("这是一个非常非常长的需求描述...", AppSyncHelper.deriveAppName(null, "这是一个非常非常长的需求描述需要被截断"));
        assertEquals("自定义名称", AppSyncHelper.deriveAppName("自定义名称", "prompt"));
    }

    @Test
    void shouldReuseExistingAppIdInResolveOrCreate() {
        assertEquals(5L, appSyncHelper.resolveOrCreateApp(5L, null, "prompt"));
        verify(codeGenRestTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void shouldResolveAppIdFromSessionBinding() {
        ChatSession session = new ChatSession();
        session.setId(10L);
        session.setAppId(7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertEquals(7L, appSyncHelper.resolveAppIdForSession(10L, null));
    }

    @Test
    void shouldReuseSessionAppOnPersistWithoutCreatingNew() {
        ChatSession session = new ChatSession();
        session.setId(10L);
        session.setAppId(7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        Long appId = appSyncHelper.persistGeneratedApp(10L, null, null, "改颜色", "<html></html>");

        assertEquals(7L, appId);
        verify(codeGenRestTemplate, never()).postForObject(anyString(), any(), any());
        verify(codeGenRestTemplate).put(eq("/api/app/{id}/code"), any(), eq(7L));
    }

    // ==================== syncCodeToApp ====================

    @Test
    void shouldSyncSingleHtmlDirectly() {
        String html = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>";
        appSyncHelper.syncCodeToApp(1L, html);

        verify(codeGenRestTemplate).put(
                eq("/api/app/{id}/code"),
                argThat((Object body) -> {
                    // 单文件 HTML 应该直接同步
                    return body.toString().contains(html);
                }),
                eq(1L)
        );
    }

    @Test
    void shouldExtractIndexHtmlFromMultiFileJson() {
        String multiFile = """
                [
                    {"path":"styles.css","content":"body{color:red;}"},
                    {"path":"index.html","content":"<html><head></head><body>Main</body></html>"},
                    {"path":"app.js","content":"console.log('hello');"}
                ]""";

        appSyncHelper.syncCodeToApp(1L, multiFile);

        verify(codeGenRestTemplate).put(
                eq("/api/app/{id}/code"),
                argThat((Object body) -> {
                    String bodyStr = body.toString();
                    return bodyStr.contains("<html>") && bodyStr.contains("Main")
                            && !bodyStr.contains("styles.css");
                }),
                eq(1L)
        );
    }

    @Test
    void shouldFallbackToFirstFileWhenNoIndexHtml() {
        String multiFile = """
                [
                    {"path":"styles.css","content":"body{color:red;}"},
                    {"path":"app.js","content":"console.log('hello');"}
                ]""";

        appSyncHelper.syncCodeToApp(1L, multiFile);

        verify(codeGenRestTemplate).put(
                eq("/api/app/{id}/code"),
                argThat((Object body) -> {
                    String bodyStr = body.toString();
                    return bodyStr.contains("body{color:red;}");
                }),
                eq(1L)
        );
    }

    @Test
    void shouldNotThrowExceptionWhenApiCallFails() {
        doThrow(new RuntimeException("Connection refused"))
                .when(codeGenRestTemplate)
                .put(anyString(), any(), anyLong());

        // 不应抛出异常，主流程继续
        appSyncHelper.syncCodeToApp(1L, "<html></html>");
    }

    @Test
    void shouldHandleEmptyContent() {
        appSyncHelper.syncCodeToApp(1L, "");
        // 空内容提前返回，不调 RestTemplate
        verify(codeGenRestTemplate, never()).put(anyString(), any(), anyLong());
    }

    @Test
    void shouldHandleNullContent() {
        appSyncHelper.syncCodeToApp(1L, null);
        // null 提前返回，不调 RestTemplate
        verify(codeGenRestTemplate, never()).put(anyString(), any(), anyLong());
    }

    // ==================== getAppConfig ====================

    @Test
    void shouldReturnConfigWhenApiCallSucceeds() {
        String respJson = """
                {"code":200,"data":{"id":1,"appName":"测试应用","description":"一个测试App"}}""";
        when(codeGenRestTemplate.getForObject(anyString(), eq(String.class), anyLong()))
                .thenReturn(respJson);

        AppSyncHelper.AppConfigDTO config = appSyncHelper.getAppConfig(1L);
        assertNotNull(config);
        assertNotNull(config.getTemperature());
        assertNotNull(config.getPromptTemplate());
        assertEquals(0, config.getTemperature().compareTo(new java.math.BigDecimal("0.7")));
        assertTrue(config.getPromptTemplate().contains("测试应用"));
        assertTrue(config.getPromptTemplate().contains("一个测试App"));
    }

    @Test
    void shouldReturnDefaultConfigWhenApiCallFails() {
        when(codeGenRestTemplate.getForObject(anyString(), eq(String.class), anyLong()))
                .thenThrow(new RuntimeException("Connection refused"));

        AppSyncHelper.AppConfigDTO config = appSyncHelper.getAppConfig(1L);
        assertNotNull(config);
        assertEquals(0, config.getTemperature().compareTo(new java.math.BigDecimal("0.7")));
        assertNotNull(config.getPromptTemplate());
    }

    @Test
    void shouldReturnDefaultConfigWhenAppIdIsNull() {
        AppSyncHelper.AppConfigDTO config = appSyncHelper.getAppConfig(null);
        assertNotNull(config);
        assertNotNull(config.getTemperature());
        assertNotNull(config.getPromptTemplate());
        // null appId 不调用 RestTemplate，直接返回默认配置
        verify(codeGenRestTemplate, never()).getForObject(anyString(), any(), anyLong());
    }
}
