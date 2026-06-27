package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import com.ai.agentplatform.module.app.deploy.vo.CoverResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 保存前端从当前预览界面截取的封面图（不依赖 Selenium）。
 */
@Service
@RequiredArgsConstructor
public class CoverImageStoreService {

    private static final long MAX_SIZE = 15 * 1024 * 1024;

    private final AppDeployAccessor appDeployAccessor;
    private final DeployPathResolver deployPathResolver;
    private final ShareUrlResolver shareUrlResolver;

    public CoverResultVO saveFromUpload(Long appId, MultipartFile file) throws IOException {
        appDeployAccessor.requireAppExists(appId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传封面图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("封面图片不能超过 15MB");
        }
        return savePngBytes(appId, file.getBytes());
    }

    public CoverResultVO savePngBytes(Long appId, byte[] pngBytes) throws IOException {
        appDeployAccessor.requireAppExists(appId);
        if (pngBytes == null || pngBytes.length == 0) {
            throw new BusinessException("封面图片为空");
        }

        Path coverFile = deployPathResolver.resolve("covers").resolve(appId + ".png");
        Files.createDirectories(coverFile.getParent());
        Files.write(coverFile, pngBytes);

        String coverPath = "/covers/" + appId + ".png";
        appDeployAccessor.updateCoverImg(appId, coverPath);
        String coverUrl = shareUrlResolver.buildShareUrl(coverPath);
        return new CoverResultVO(appId, coverUrl);
    }
}
