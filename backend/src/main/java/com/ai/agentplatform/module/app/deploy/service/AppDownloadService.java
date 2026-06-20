package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AppDownloadService {

    private final AppDeployAccessor appDeployAccessor;
    private final AppCodeFileService appCodeFileService;

    public Resource buildZipResource(Long appId) throws IOException {
        appDeployAccessor.requireAppExists(appId);
        String appName = appDeployAccessor.getAppName(appId);
        List<AppCodeFile> files = appCodeFileService.resolveCodeFiles(appId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (AppCodeFile file : files) {
                String entryName = file.getPath() != null ? file.getPath() : "index.html";
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                byte[] content = (file.getContent() != null ? file.getContent() : "").getBytes(StandardCharsets.UTF_8);
                zos.write(content);
                zos.closeEntry();
            }
        }

        byte[] zipBytes = baos.toByteArray();
        if (zipBytes.length == 0) {
            throw new BusinessException("暂无可下载的源码");
        }

        return new NamedByteArrayResource(zipBytes, sanitizeFileName(appName) + ".zip");
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
