package com.ai.agentplatform.module.app.deploy.service.strategy;

import com.ai.agentplatform.common.exception.BusinessException;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerDeployStrategy implements DeployStrategy {

    private final AppCodeFileService appCodeFileService;
    private final DeployPathResolver deployPathResolver;
    private final DeployProperties deployProperties;
    private final ShareUrlResolver shareUrlResolver;

    @Override
    public DeployMode mode() {
        return DeployMode.DOCKER;
    }

    @Override
    public DeployOutcome deploy(Long appId, List<AppCodeFile> files, String entryPath)
            throws IOException, InterruptedException {
        if (!ProcessCommandRunner.isCommandAvailable("docker")) {
            throw new BusinessException("未检测到 Docker，请先安装 Docker Desktop 并确保 docker 命令可用");
        }

        String dockerSubDir = deployProperties.getDockerSubDir();
        Path dockerDir = deployPathResolver.resolve(dockerSubDir, "app-" + appId);
        appCodeFileService.writeToDirectory(dockerDir, files);
        writeDockerfile(dockerDir);

        int hostPort = deployProperties.getDockerBasePort() + appId.intValue();
        String imageName = "ai-agent-app-" + appId;
        String containerName = "ai-agent-site-" + appId;

        stopContainerIfExists(containerName);
        ProcessCommandRunner.run(dockerDir, 300, "docker", "build", "-t", imageName, ".");
        ProcessCommandRunner.run(null, 60, "docker", "run", "-d",
                "--name", containerName,
                "-p", hostPort + ":80",
                "--restart", "unless-stopped",
                imageName);

        String portRef = String.valueOf(hostPort);
        String shareUrl = shareUrlResolver.buildShareUrl(
                new DeployUrlCodec.DeployReference(DeployMode.DOCKER, portRef));

        return new DeployOutcome(portRef, shareUrl,
                "Docker 部署成功（容器 " + containerName + "，端口 " + hostPort + "）");
    }

    private void writeDockerfile(Path dockerDir) throws IOException {
        Path dockerfile = dockerDir.resolve("Dockerfile");
        if (Files.exists(dockerfile)) {
            return;
        }
        String content = """
                FROM nginx:1.27-alpine
                COPY . /usr/share/nginx/html/
                RUN printf 'charset utf-8;\\n' > /etc/nginx/conf.d/charset.conf
                EXPOSE 80
                """;
        Files.writeString(dockerfile, content, StandardCharsets.UTF_8);
    }

    private void stopContainerIfExists(String containerName) {
        try {
            ProcessCommandRunner.run(null, 30, "docker", "rm", "-f", containerName);
        } catch (Exception e) {
            log.debug("容器 {} 不存在或已停止: {}", containerName, e.getMessage());
        }
    }
}
