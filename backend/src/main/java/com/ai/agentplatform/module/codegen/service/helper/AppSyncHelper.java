package com.ai.agentplatform.module.codegen.service.helper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 应用模块同步辅助工具
 * TODO D5波次2集成：调用成员2 PUT /api/app/{id}/code 更新应用代码
 */
@Slf4j
@Component
public class AppSyncHelper {

    /**
     * Mock同步代码到应用模块，仅打印日志
     */
    public void syncCodeToApp(Long appId, String fullCode) {
        log.info("[Mock应用同步] appId={}, 代码总长度={}", appId, fullCode.length());
    }

    /**
     * 获取应用自定义配置（Mock默认参数）
     * TODO D5：查询app表读取自定义prompt、temperature
     */
    public AppConfigDTO getAppConfig(Long appId) {
        AppConfigDTO config = new AppConfigDTO();
        config.setTemperature(new BigDecimal("0.7"));
        config.setPromptTemplate("只输出纯净代码，无多余解释、markdown标记，严格匹配生成类型规范");
        log.info("[Mock读取应用{}默认配置]", appId);
        return config;
    }

    // 内部类必须加@Data生成get/set
    @Data
    public static class AppConfigDTO {
        private BigDecimal temperature;
        private String promptTemplate;
    }
}