package com.ai.agentplatform.module.app.deploy.support;

import com.ai.agentplatform.module.app.deploy.config.DeployProperties;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一部署文件根目录，兼容从项目根目录或 backend 子目录启动。
 */
@Component
@Getter
public class DeployPathResolver {

    private final Path root;

    public DeployPathResolver(DeployProperties deployProperties) {
        this.root = locateRoot(deployProperties.getBasePath());
    }

    public Path resolve(String... segments) {
        Path path = root;
        for (String segment : segments) {
            path = path.resolve(segment);
        }
        return path.normalize();
    }

    private static Path locateRoot(String basePath) {
        Path configured = Path.of(basePath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        String userDir = System.getProperty("user.dir");
        List<Path> candidates = List.of(
                Path.of(userDir, basePath),
                Path.of(userDir, "backend", basePath)
        );
        for (Path candidate : candidates) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(absolute)) {
                return absolute;
            }
        }
        return Path.of(userDir, basePath).toAbsolutePath().normalize();
    }
}
