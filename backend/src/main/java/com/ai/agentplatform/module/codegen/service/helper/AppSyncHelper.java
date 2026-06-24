package com.ai.agentplatform.module.codegen.service.helper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 应用模块同步辅助工具
 * 调用成员2 PUT /api/app/{id}/code 更新应用代码
 *
 * <p>多文件格式约定（与成员2、3协作）：</p>
 * <ul>
 *   <li>单文件 HTML：app_code 直接存原始 HTML</li>
 *   <li>多文件 MULTI_FILE：code_generate.code_content 存完整 JSON 数组；
 *       app.app_code 提取首个 index.html 内容，保证预览模块可直接渲染</li>
 * </ul>
 */
@Slf4j
@Component
public class AppSyncHelper {

    @Resource
    private RestTemplate codeGenRestTemplate;

    /** 成员2 更新代码接口路径 */
    private static final String APP_CODE_UPDATE_URL = "/api/app/{id}/code";

    /**
     * 真实同步代码到应用模块
     * 调用成员2 PUT /api/app/{id}/code，将生成的代码写入 app.app_code 字段
     * 多文件场景自动提取 index.html 内容，保证成员3预览模块可直接渲染
     */
    public void syncCodeToApp(Long appId, String fullCode) {
        if (fullCode == null || fullCode.isBlank()) {
            log.warn("[应用同步] appId={}, fullCode 为空，跳过同步", appId);
            return;
        }
        try {
            String previewContent = extractPreviewContent(fullCode);
            Map<String, String> body = Map.of("codeContent", previewContent);
            codeGenRestTemplate.put(APP_CODE_UPDATE_URL, body, appId);
            log.info("[应用同步] appId={}, 代码长度={}, 已写入 app.app_code", appId, previewContent.length());
        } catch (Exception e) {
            // 同步失败不阻塞代码生成主流程，仅记录错误日志
            log.error("[应用同步失败] appId={}, 原因: {}", appId, e.getMessage(), e);
        }
    }

    /**
     * 多文件JSON → 提取可预览的 HTML 内容
     * 优先取 index.html，找不到则取第一个文件内容；非 JSON 原样返回
     */
    private String extractPreviewContent(String fullCode) {
        if (fullCode == null || fullCode.isBlank()) {
            return fullCode;
        }
        String trimmed = fullCode.trim();
        if (!trimmed.startsWith("[")) {
            return fullCode;
        }
        try {
            JSONArray files = JSON.parseArray(trimmed);
            // 优先查找 index.html
            for (int i = 0; i < files.size(); i++) {
                JSONObject file = files.getJSONObject(i);
                String path = file.getString("path");
                if (path != null && (path.endsWith("index.html") || path.equals("index.html"))) {
                    String content = file.getString("content");
                    log.debug("[多文件提取] 找到 index.html, 内容长度={}", content != null ? content.length() : 0);
                    return content != null ? content : fullCode;
                }
            }
            // 无 index.html，取第一个文件内容
            if (!files.isEmpty()) {
                JSONObject firstFile = files.getJSONObject(0);
                String content = firstFile.getString("content");
                log.debug("[多文件提取] 无 index.html，取首个文件: {}", firstFile.getString("path"));
                return content != null ? content : fullCode;
            }
        } catch (Exception e) {
            log.warn("[多文件提取] JSON 解析失败，使用原始内容同步", e);
        }
        return fullCode;
    }

    /**
     * 获取应用自定义配置
     * 通过 RestTemplate 调用成员2 GET /api/app/{id} 读取真实 App 信息
     * 调用失败时兜底返回默认配置
     */
    public AppConfigDTO getAppConfig(Long appId) {
        if (appId == null) {
            return buildDefaultConfig();
        }
        try {
            String url = APP_CODE_UPDATE_URL.replace("/{id}/code", "/{id}");
            // 调用成员2 GET /api/app/{id}
            String respJson = codeGenRestTemplate.getForObject(url, String.class, appId);
            JSONObject respObj = JSON.parseObject(respJson);
            if (respObj == null || respObj.getIntValue("code") != 200) {
                log.warn("[读取App配置] appId={} 接口返回异常，使用默认配置", appId);
                return buildDefaultConfig();
            }
            JSONObject appData = respObj.getJSONObject("data");
            if (appData == null) {
                return buildDefaultConfig();
            }
            AppConfigDTO config = new AppConfigDTO();
            config.setTemperature(new BigDecimal("0.7"));
            // 使用真实 App 名称和描述丰富 Prompt 上下文
            String appName = appData.getString("appName");
            String description = appData.getString("description");
            StringBuilder promptTpl = new StringBuilder();
            promptTpl.append("只输出纯净代码，无多余解释、markdown标记，严格匹配生成类型规范。");
            if (appName != null && !appName.isBlank()) {
                promptTpl.append("当前应用：").append(appName).append("。");
            }
            if (description != null && !description.isBlank()) {
                promptTpl.append("应用描述：").append(description).append("。");
            }
            config.setPromptTemplate(promptTpl.toString());
            log.info("[读取App配置] appId={}, appName={}, 已加载真实配置", appId, appName);
            return config;
        } catch (Exception e) {
            log.warn("[读取App配置失败] appId={}, 使用默认配置兜底, 原因: {}", appId, e.getMessage());
            return buildDefaultConfig();
        }
    }

    private AppConfigDTO buildDefaultConfig() {
        AppConfigDTO config = new AppConfigDTO();
        config.setTemperature(new BigDecimal("0.7"));
        config.setPromptTemplate("只输出纯净代码，无多余解释、markdown标记，严格匹配生成类型规范");
        return config;
    }

    @Data
    public static class AppConfigDTO {
        private BigDecimal temperature;
        private String promptTemplate;
    }
}