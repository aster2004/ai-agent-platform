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

    /** 分享链接主机（IP 或域名），留空则自动检测局域网 IP */
    private String shareHost = "";

    /** 是否启用 Selenium 封面截图（P2，需本机 Chrome） */
    private boolean screenshotEnabled = false;

    /** Nginx 部署监听端口 */
    private int nginxPort = 8888;

    /** Nginx 配置子目录（相对 deploy 根目录） */
    private String nginxConfDir = "nginx/conf";

    /** Nginx 静态站点子目录 */
    private String nginxSitesSubDir = "nginx-sites";

    /** Nginx 可执行文件路径，留空则不自动 reload */
    private String nginxExecutable = "";

    /**
     * Nginx 安装根目录（仅 ASCII 路径，如 D:/nginx-1.15.12）。
     * 配置后静态文件写入 html/apps/{id}/，配置写入 conf/ai-agent/，避免中文路径导致 Windows Nginx 失败。
     */
    private String nginxInstallPath = "";

    /** Docker 部署起始端口，实际端口 = dockerBasePort + appId */
    private int dockerBasePort = 9000;

    /** Docker 构建上下文子目录 */
    private String dockerSubDir = "docker";
}
