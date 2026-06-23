package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.module.app.deploy.config.DeployProperties;
import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppCodeFileService {

    private static final String DEFAULT_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8"/>
              <title>Mock Preview</title>
              <style>
                body { font-family: sans-serif; display: flex; align-items: center;
                       justify-content: center; min-height: 100vh; background: #f5f7fa; margin: 0; }
                .card { background: #fff; padding: 48px; border-radius: 12px;
                        box-shadow: 0 4px 24px rgba(0,0,0,.08); text-align: center; }
                h1 { color: #1677ff; margin: 0 0 12px; }
                p { color: #666; margin: 0; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>Hello Mock Preview</h1>
                <p>并行开发期默认预览页，接入真实 app_code 后自动替换</p>
              </div>
            </body>
            </html>
            """;

    private final AppDeployAccessor appDeployAccessor;
    private final DeployProperties deployProperties;
    private final DeployPathResolver deployPathResolver;
    private final ObjectMapper objectMapper;

    public List<AppCodeFile> resolveCodeFiles(Long appId) {
        String appCode = appDeployAccessor.getAppCode(appId);
        if (!StringUtils.hasText(appCode)) {
            return List.of(singleFile("index.html", DEFAULT_HTML));
        }
        String trimmed = appCode.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<AppCodeFile> files = objectMapper.readValue(trimmed, new TypeReference<>() {});
                if (files.isEmpty()) {
                    return List.of(singleFile("index.html", DEFAULT_HTML));
                }
                return files;
            } catch (IOException e) {
                log.warn("解析多文件 app_code 失败，按单文件 HTML 处理: appId={}", appId, e);
            }
        }
        return List.of(singleFile("index.html", appCode));
    }

    public void writeFiles(Long appId, String subDir, List<AppCodeFile> files) throws IOException {
        writeToDirectory(deployPathResolver.resolve(subDir, String.valueOf(appId)), files);
    }

    public void writeToDirectory(Path targetDir, List<AppCodeFile> files) throws IOException {
        targetDir = targetDir.toAbsolutePath().normalize();
        if (Files.exists(targetDir)) {
            deleteRecursively(targetDir);
        }
        Files.createDirectories(targetDir);
        for (AppCodeFile file : files) {
            String relativePath = StringUtils.hasText(file.getPath()) ? file.getPath() : "index.html";
            Path filePath = targetDir.resolve(relativePath).normalize();
            if (!filePath.startsWith(targetDir)) {
                throw new IllegalArgumentException("非法文件路径: " + relativePath);
            }
            Files.createDirectories(filePath.getParent());
            String content = file.getContent() != null ? file.getContent() : "";
            if (relativePath.toLowerCase().endsWith(".html") || relativePath.toLowerCase().endsWith(".htm")) {
                content = ensureUtf8HtmlDocument(content);
            }
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
        }
    }

    /**
     * AI 生成的 app_code 常为 HTML 片段且无 charset，在 Windows 下会被浏览器按 GBK 解析导致乱码。
     */
    private String ensureUtf8HtmlDocument(String content) {
        if (!StringUtils.hasText(content)) {
            return DEFAULT_HTML;
        }
        String trimmed = content.trim();
        String lower = trimmed.toLowerCase();
        boolean hasHtmlTag = lower.contains("<html");
        boolean hasCharset = lower.contains("charset=\"utf-8\"")
                || lower.contains("charset='utf-8'")
                || lower.contains("charset=utf-8");

        if (hasHtmlTag && hasCharset) {
            return content;
        }
        if (hasHtmlTag) {
            return injectCharsetMeta(content);
        }
        return wrapHtmlFragment(content);
    }

    private String injectCharsetMeta(String html) {
        String lower = html.toLowerCase();
        int headIdx = lower.indexOf("<head");
        if (headIdx >= 0) {
            int headEnd = lower.indexOf('>', headIdx);
            if (headEnd >= 0) {
                return html.substring(0, headEnd + 1)
                        + "\n  <meta charset=\"UTF-8\"/>"
                        + html.substring(headEnd + 1);
            }
        }
        int htmlIdx = lower.indexOf("<html");
        if (htmlIdx >= 0) {
            int htmlEnd = lower.indexOf('>', htmlIdx);
            if (htmlEnd >= 0) {
                return html.substring(0, htmlEnd + 1)
                        + "\n<head><meta charset=\"UTF-8\"/></head>"
                        + html.substring(htmlEnd + 1);
            }
        }
        return wrapHtmlFragment(html);
    }

    private String wrapHtmlFragment(String bodyContent) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>App Preview</title>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(bodyContent);
    }

    public String buildPublicUrl(String path) {
        if (StringUtils.hasText(deployProperties.getPublicBaseUrl())) {
            String base = deployProperties.getPublicBaseUrl();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + path;
        }
        return path;
    }

    private AppCodeFile singleFile(String path, String content) {
        AppCodeFile file = new AppCodeFile();
        file.setPath(path);
        file.setContent(content);
        return file;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
