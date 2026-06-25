package com.ai.agentplatform.module.codegen.strategy;

import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HtmlMultiStrategy implements CodeGenStrategy {

    /** 多文件固定输出格式示例：[{"path":"index.html","content":"<html></html>"}] */
    private static final String MULTI_FILE_FORMAT_TIP = "\n必须严格只输出纯JSON数组，格式：[{\"path\":\"文件相对路径\",\"content\":\"文件完整代码\"}]，禁止任何解释文字、markdown标记、额外注释";

    @Override
    public boolean support(String generateType) {
        return CodeGenConstant.GENERATE_TYPE_MULTI_FILE.equals(generateType);
    }

    @Override
    public String buildSpecialPrompt(String basePrompt, CodeGenRequest request) {
        // 在原有系统提示后追加多文件强制格式要求
        return basePrompt + MULTI_FILE_FORMAT_TIP;
    }

    /**
     * 解析AI返回文本，自动清洗markdown代码块，校验标准JSON数组
     * 失败则包装异常文本，保证入库不报错
     */
    @Override
    public String parseResult(String aiRawText) {
        String cleanText = cleanMarkdownTag(aiRawText);
        try {
            // 校验是否为合法JSON数组
            JSONArray array = JSON.parseArray(cleanText);
            return cleanText;
        } catch (Exception e) {
            log.error("MULTI_FILE多文件AI返回格式非法，原始文本：{}", aiRawText, e);
            // 解析失败原样存储，前端做异常提示
            return aiRawText;
        }
    }

    /**
     * 移除AI自带 ```json / ``` 标记
     */
    private String cleanMarkdownTag(String text) {
        if (text.startsWith("```json")) {
            text = text.replaceFirst("```json", "");
        }
        if (text.startsWith("```")) {
            text = text.replaceFirst("```", "");
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.lastIndexOf("```"));
        }
        return text.trim();
    }
}