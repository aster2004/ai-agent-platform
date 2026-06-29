package com.ai.agentplatform.module.app.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.config.AppUploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppCoverService {

    private static final long MAX_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final AppUploadProperties uploadProperties;

    public String saveCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择封面图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("封面图片不能超过 2MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 JPG、PNG、WebP、GIF 格式");
        }

        String ext = resolveExtension(file.getOriginalFilename(), contentType);
        String filename = UUID.randomUUID() + ext;

        Path coverDir = Paths.get(uploadProperties.getPath(), "cover").toAbsolutePath().normalize();
        try {
            Files.createDirectories(coverDir);
            Path target = coverDir.resolve(filename);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/api/app/cover/" + filename;
        } catch (IOException e) {
            throw new BusinessException("封面保存失败");
        }
    }

    public byte[] getCover(String filename) {
        if (!StringUtils.hasText(filename) || filename.contains("..") || filename.contains("/")) {
            return null;
        }
        Path coverDir = Paths.get(uploadProperties.getPath(), "cover").toAbsolutePath().normalize();
        Path filePath = coverDir.resolve(filename).normalize();
        if (!filePath.startsWith(coverDir) || !Files.isRegularFile(filePath)) {
            return null;
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            return null;
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename)) {
            String name = originalFilename.toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                return ".jpg";
            }
            if (name.endsWith(".png")) {
                return ".png";
            }
            if (name.endsWith(".webp")) {
                return ".webp";
            }
            if (name.endsWith(".gif")) {
                return ".gif";
            }
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
