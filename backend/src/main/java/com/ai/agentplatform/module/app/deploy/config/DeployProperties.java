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

    /** 分享链接使用的 host（如 192.168.1.100），留空则自动检测局域网 IP */
    private String shareHost = "";

    /** 是否启用 Selenium 封面截图（P2，需本机 Chrome） */
    private boolean screenshotEnabled = false;
}
