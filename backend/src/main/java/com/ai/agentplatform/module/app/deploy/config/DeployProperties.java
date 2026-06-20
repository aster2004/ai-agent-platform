package com.ai.agentplatform.module.app.deploy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.deploy")
public class DeployProperties {

    /** 本地部署文件根目录 */
    private String basePath = "deploy";

    /** 对外访问基础 URL，留空则返回相对路径（如 /preview/1/index.html） */
    private String publicBaseUrl = "";

    /** 是否启用 Selenium 封面截图（P2，需本机 Chrome） */
    private boolean screenshotEnabled = false;
}
