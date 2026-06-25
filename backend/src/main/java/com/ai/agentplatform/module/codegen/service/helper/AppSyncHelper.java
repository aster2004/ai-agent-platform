package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.app.entity.App;
import com.ai.agentplatform.module.app.repository.AppRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppSyncHelper {

    private final AppRepository appRepository;
    private final ObjectMapper objectMapper;

    public void syncCodeToApp(Long appId, String fullCode) {
        appRepository.findById(appId).ifPresentOrElse(app -> {
            app.setAppCode(fullCode);
            appRepository.save(app);
            log.info("[应用同步] appId={}, 代码长度={}", appId, fullCode.length());
        }, () -> log.warn("[应用同步] appId={} 不存在，跳过同步", appId));
    }

    public void syncCodeFilesToApp(Long appId, List<CodeFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        try {
            if (files.size() == 1 && "index.html".equals(files.get(0).getPath())) {
                syncCodeToApp(appId, files.get(0).getContent());
            } else {
                syncCodeToApp(appId, objectMapper.writeValueAsString(files));
            }
        } catch (Exception e) {
            log.error("同步多文件代码失败 appId={}", appId, e);
        }
    }

    public AppConfigDTO getAppConfig(Long appId) {
        AppConfigDTO config = new AppConfigDTO();
        config.setTemperature(new BigDecimal("0.7"));
        config.setPromptTemplate("只输出纯净代码，无多余解释、markdown标记，严格匹配生成类型规范");
        appRepository.findById(appId).ifPresent(app ->
                log.info("[读取应用配置] appId={}, appName={}", appId, app.getAppName()));
        return config;
    }

    @Data
    public static class AppConfigDTO {
        private BigDecimal temperature;
        private String promptTemplate;
    }
}
