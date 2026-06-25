package com.ai.agentplatform.module.codegen.strategy;

import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import org.springframework.stereotype.Component;

@Component
public class HtmlSingleStrategy implements CodeGenStrategy {

    @Override
    public boolean support(String generateType) {
        return CodeGenConstant.GENERATE_TYPE_HTML.equals(generateType);
    }

    /** 单文件 HTML 专属约束 */
    private static final String SINGLE_FILE_CONSTRAINT =
            "\n必须严格只输出一份完整的 HTML 文件代码，要求："
                    + "1) 所有 CSS 内联于 <style> 标签中，不引入外部样式表；"
                    + "2) 不依赖外部 JS 框架（如 Vue/React），仅使用原生 JavaScript；"
                    + "3) 禁止任何 markdown 标记、代码块包裹、解释性文字；"
                    + "4) 页面结构完整，包含 <!DOCTYPE html> 声明。";

    @Override
    public String buildSpecialPrompt(String basePrompt, CodeGenRequest request) {
        return basePrompt + SINGLE_FILE_CONSTRAINT;
    }

    @Override
    public String parseResult(String aiRawText) {
        // 单文件直接原样返回HTML文本
        return aiRawText;
    }
}