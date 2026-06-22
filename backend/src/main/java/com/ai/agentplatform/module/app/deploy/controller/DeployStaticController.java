package com.ai.agentplatform.module.app.deploy.controller;

import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 直接读取 deploy 目录下的静态文件，避免 ResourceHandler 在 Windows 上的 file URI 拼接问题。
 */
@Controller
@RequiredArgsConstructor
public class DeployStaticController {

    private final DeployPathResolver deployPathResolver;

    @GetMapping("/preview/**")
    public ResponseEntity<Resource> preview(HttpServletRequest request) {
        return serve(request, "preview");
    }

    @GetMapping("/sites/**")
    public ResponseEntity<Resource> sites(HttpServletRequest request) {
        return serve(request, "sites");
    }

    @GetMapping("/covers/**")
    public ResponseEntity<Resource> covers(HttpServletRequest request) {
        return serve(request, "covers");
    }

    private ResponseEntity<Resource> serve(HttpServletRequest request, String subDir) {
        String uri = request.getRequestURI();
        String prefix = "/" + subDir + "/";
        if (!uri.startsWith(prefix)) {
            return ResponseEntity.notFound().build();
        }
        String relativePath = uri.substring(prefix.length());
        if (relativePath.isBlank() || relativePath.contains("..")) {
            return ResponseEntity.notFound().build();
        }

        Path baseDir = deployPathResolver.resolve(subDir).toAbsolutePath().normalize();
        Path file = baseDir.resolve(relativePath).normalize();
        if (!file.startsWith(baseDir) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(resolveMediaType(file))
                .body(resource);
    }

    private MediaType resolveMediaType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return MediaType.parseMediaType("text/html;charset=UTF-8");
        }
        if (name.endsWith(".css")) {
            return MediaType.parseMediaType("text/css;charset=UTF-8");
        }
        if (name.endsWith(".js")) {
            return MediaType.parseMediaType("application/javascript;charset=UTF-8");
        }
        try {
            String probed = Files.probeContentType(file);
            if (probed != null) {
                return MediaType.parseMediaType(probed);
            }
        } catch (IOException ignored) {
            // fall through
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
