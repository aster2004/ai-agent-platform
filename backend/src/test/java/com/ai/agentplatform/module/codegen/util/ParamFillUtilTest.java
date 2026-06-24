package com.ai.agentplatform.module.codegen.util;

import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ParamFillUtil 参数兜底单元测试
 */
@ExtendWith(MockitoExtension.class)
class ParamFillUtilTest {

    private final ParamFillUtil util = new ParamFillUtil();

    // ==================== fillGenerateType ====================

    @Test
    void shouldReturnHtmlWhenGenerateTypeIsNull() {
        assertEquals(CodeGenConstant.GENERATE_TYPE_HTML, util.fillGenerateType(null));
    }

    @Test
    void shouldReturnHtmlWhenGenerateTypeIsBlank() {
        assertEquals(CodeGenConstant.GENERATE_TYPE_HTML, util.fillGenerateType(""));
        assertEquals(CodeGenConstant.GENERATE_TYPE_HTML, util.fillGenerateType("   "));
    }

    @Test
    void shouldPreserveValidGenerateType() {
        assertEquals("HTML", util.fillGenerateType("html"));
        assertEquals("VUE", util.fillGenerateType("VUE"));
        assertEquals("MULTI_FILE", util.fillGenerateType("MULTI_FILE"));
        assertEquals("WORKFLOW", util.fillGenerateType("workflow"));
    }

    @Test
    void shouldFallbackToHtmlWhenTypeIsInvalid() {
        assertEquals(CodeGenConstant.GENERATE_TYPE_HTML, util.fillGenerateType("INVALID_TYPE"));
        assertEquals(CodeGenConstant.GENERATE_TYPE_HTML, util.fillGenerateType("java"));
    }

    // ==================== fillAppId ====================

    @Test
    void shouldReturnDefaultWhenAppIdIsNull() {
        assertEquals(CodeGenConstant.MOCK_DEFAULT_APP_ID, util.fillAppId(null));
    }

    @Test
    void shouldReturnProvidedAppId() {
        assertEquals(5L, util.fillAppId(5L));
    }

    // ==================== fillModel ====================

    @Test
    void shouldReturnDefaultWhenModelNameIsNull() {
        assertEquals(CodeGenConstant.MODEL_DEEPSEEK, util.fillModel(null));
    }

    @Test
    void shouldReturnDefaultWhenModelNameIsBlank() {
        assertEquals(CodeGenConstant.MODEL_DEEPSEEK, util.fillModel(""));
        assertEquals(CodeGenConstant.MODEL_DEEPSEEK, util.fillModel("  "));
    }

    @Test
    void shouldReturnProvidedModelName() {
        assertEquals("gpt-4o-mini", util.fillModel("gpt-4o-mini"));
    }
}
