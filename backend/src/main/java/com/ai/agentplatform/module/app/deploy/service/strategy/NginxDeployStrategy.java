package com.ai.agentplatform.module.app.deploy.service.strategy;

import com.ai.agentplatform.module.app.deploy.config.DeployProperties;
import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.enums.DeployMode;
import com.ai.agentplatform.module.app.deploy.service.AppCodeFileService;
import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import com.ai.agentplatform.module.app.deploy.support.DeployUrlCodec;
import com.ai.agentplatform.module.app.deploy.support.ProcessCommandRunner;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NginxDeployStrategy implements DeployStrategy {

    private final AppCodeFileService appCodeFileService;
    private final DeployPathResolver deployPathResolver;
    private final DeployProperties deployProperties;
    private final ShareUrlResolver shareUrlResolver;

    @Override
    public DeployMode mode() {
        return DeployMode.NGINX;
    }

    @Override
    public DeployOutcome deploy(Long appId, List<AppCodeFile> files, String entryPath)
            throws IOException, InterruptedException {
        Path siteDir = resolveNginxSiteDir(appId);
        appCodeFileService.writeToDirectory(siteDir, files);

        Path confDir = resolveNginxConfDir();
        Files.createDirectories(confDir);
        ensureMainConf(confDir);
        writeAppConf(appId, siteDir, confDir);
        tryReloadNginx(confDir);

        String deployPath = "/apps/" + appId + "/" + entryPath;
        String shareUrl = shareUrlResolver.buildShareUrl(
                new DeployUrlCodec.DeployReference(DeployMode.NGINX, deployPath));

        String message = buildSuccessMessage();
        return new DeployOutcome(deployPath, shareUrl, message);
    }

    private String buildSuccessMessage() {
        String base = "Nginx 部署成功（端口 " + deployProperties.getNginxPort() + "）";
        if (StringUtils.hasText(deployProperties.getNginxInstallPath())) {
            String prefix = toNginxPath(deployProperties.getNginxInstallPath());
            if (!prefix.endsWith("/")) {
                prefix += "/";
            }
            return base + "；配置目录 " + prefix + "conf/ai-agent/";
        }
        if (!StringUtils.hasText(deployProperties.getNginxExecutable())) {
            return base + "；请配置 nginx-install-path 与 nginx-executable 后重启后端";
        }
        return base + "；请手动启动/重载 Nginx";
    }

    private Path resolveNginxConfDir() {
        if (StringUtils.hasText(deployProperties.getNginxInstallPath())) {
            return Path.of(deployProperties.getNginxInstallPath(), "conf", "ai-agent")
                    .toAbsolutePath().normalize();
        }
        return deployPathResolver.resolve(deployProperties.getNginxConfDir())
                .toAbsolutePath().normalize();
    }

    private Path resolveNginxSiteDir(Long appId) {
        if (StringUtils.hasText(deployProperties.getNginxInstallPath())) {
            return Path.of(deployProperties.getNginxInstallPath(), "html", "apps", String.valueOf(appId))
                    .toAbsolutePath().normalize();
        }
        return deployPathResolver.resolve(deployProperties.getNginxSitesSubDir(), String.valueOf(appId))
                .toAbsolutePath().normalize();
    }

    private void ensureMainConf(Path confDir) throws IOException {
        Path mainConf = confDir.resolve("nginx-main.conf");
        String includePath = toNginxPath(confDir.toString());
        if (!includePath.endsWith("/")) {
            includePath += "/";
        }
        includePath += "app-*.conf";
        String errorLog = resolveLogPath("ai-agent-error.log");
        String accessLog = resolveLogPath("ai-agent-access.log");
        String mimeTypes = resolveMimeTypesPath(confDir);

        String content = """
                worker_processes  1;
                error_log  %s;
                pid        logs/ai-agent-nginx.pid;
                events { worker_connections  1024; }
                http {
                    include       %s;
                    default_type  application/octet-stream;
                    access_log    %s;
                    sendfile        on;
                    keepalive_timeout  65;
                    charset utf-8;
                    server {
                        listen       %d;
                        server_name  _;
                        include %s;
                    }
                }
                """.formatted(errorLog, mimeTypes, accessLog, deployProperties.getNginxPort(), includePath);
        Files.writeString(mainConf, content, StandardCharsets.UTF_8);
    }

    private String resolveMimeTypesPath(Path confDir) {
        if (StringUtils.hasText(deployProperties.getNginxInstallPath())) {
            return toNginxPath(Path.of(deployProperties.getNginxInstallPath(), "conf", "mime.types")
                    .toAbsolutePath().normalize().toString());
        }
        return "mime.types";
    }

    private String resolveLogPath(String fileName) throws IOException {
        if (StringUtils.hasText(deployProperties.getNginxInstallPath())) {
            Path logDir = Path.of(deployProperties.getNginxInstallPath(), "logs").toAbsolutePath().normalize();
            Files.createDirectories(logDir);
            return toNginxPath(logDir.resolve(fileName).toString());
        }
        return toNginxPath(fileName);
    }

    private void writeAppConf(Long appId, Path siteDir, Path confDir) throws IOException {
        String aliasPath = toNginxPath(siteDir.toString());
        if (!aliasPath.endsWith("/")) {
            aliasPath += "/";
        }
        String content = """
                # auto-generated for app %d
                location /apps/%d/ {
                    alias "%s";
                    index index.html;
                    charset utf-8;
                }
                """.formatted(appId, appId, aliasPath);
        Files.writeString(confDir.resolve("app-" + appId + ".conf"), content, StandardCharsets.UTF_8);
    }

    private void tryReloadNginx(Path confDir) throws IOException, InterruptedException {
        String nginxExe = deployProperties.getNginxExecutable();
        if (!StringUtils.hasText(nginxExe)) {
            return;
        }
        if (StringUtils.hasText(deployProperties.getNginxInstallPath())) {
            String prefix = toNginxPath(deployProperties.getNginxInstallPath());
            if (!prefix.endsWith("/")) {
                prefix += "/";
            }
            String confRel = "conf/ai-agent/nginx-main.conf";
            ProcessCommandRunner.run(null, 30, nginxExe, "-p", prefix, "-t", "-c", confRel);
            if (!tryReloadOrStart(nginxExe, prefix, confRel)) {
                log.warn("Nginx 未自动启动/重载，请手动执行: {} -p {} -c {}", nginxExe, prefix, confRel);
            }
            return;
        }
        Path mainConf = confDir.resolve("nginx-main.conf");
        String mainConfPath = mainConf.toString();
        ProcessCommandRunner.run(null, 30, nginxExe, "-t", "-c", mainConfPath);
        if (!tryReloadOrStart(nginxExe, null, mainConfPath)) {
            log.warn("Nginx 未自动启动/重载，请手动执行: {} -c {}", nginxExe, mainConfPath);
        }
    }

    private boolean tryReloadOrStart(String nginxExe, String prefix, String confArg)
            throws IOException, InterruptedException {
        try {
            if (prefix != null) {
                ProcessCommandRunner.run(null, 30, nginxExe, "-p", prefix, "-s", "reload");
            } else {
                ProcessCommandRunner.run(null, 30, nginxExe, "-s", "reload");
            }
            log.info("Nginx 配置已重载");
            return true;
        } catch (Exception reloadError) {
            log.info("Nginx reload 失败，尝试首次启动: {}", reloadError.getMessage());
            try {
                if (prefix != null) {
                    ProcessCommandRunner.startDetached(null, nginxExe, "-p", prefix, "-c", confArg);
                } else {
                    ProcessCommandRunner.startDetached(null, nginxExe, "-c", confArg);
                }
                log.info("Nginx 已在后台启动");
                return true;
            } catch (Exception startError) {
                log.warn("Nginx 启动失败: {}", startError.getMessage());
                return false;
            }
        }
    }

    private String toNginxPath(String path) {
        return path.replace("\\", "/");
    }
}
