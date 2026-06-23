package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import com.ai.agentplatform.module.app.deploy.vo.CoverResultVO;
import com.ai.agentplatform.module.app.deploy.vo.PreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * P2：Selenium 封面截图。需在 application.yml 设置 app.deploy.screenshot-enabled=true，
 * 且本机已安装 Chrome 与对应 ChromeDriver。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.deploy.screenshot-enabled", havingValue = "true")
public class CoverScreenshotService {

    private final AppPreviewService appPreviewService;
    private final AppDeployAccessor appDeployAccessor;
    private final DeployPathResolver deployPathResolver;
    private final ShareUrlResolver shareUrlResolver;

    public CoverResultVO captureCover(Long appId) throws IOException, InterruptedException {
        PreviewVO preview = appPreviewService.buildPreview(appId);
        String previewUrl = toAbsoluteUrl(preview.getPreviewUrl());

        Path coverFile = deployPathResolver.resolve("covers").resolve(appId + ".png");
        Files.createDirectories(coverFile.getParent());
        captureWithSelenium(previewUrl, coverFile);

        String coverPath = "/covers/" + appId + ".png";
        appDeployAccessor.updateCoverImg(appId, coverPath);
        String coverUrl = shareUrlResolver.buildShareUrl(coverPath);
        return new CoverResultVO(appId, coverUrl);
    }

    private void captureWithSelenium(String url, Path outputPath) throws InterruptedException {
        org.openqa.selenium.WebDriver driver = null;
        try {
            var options = new org.openqa.selenium.chrome.ChromeOptions();
            options.addArguments("--headless=new", "--window-size=1280,720", "--disable-gpu");
            driver = new org.openqa.selenium.chrome.ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.get(url);
            Thread.sleep(1500);
            byte[] screenshot = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            Files.write(outputPath, screenshot);
        } catch (Exception e) {
            log.error("封面截图失败: url={}", url, e);
            throw new BusinessException("封面截图失败，请确认已安装 Chrome 并启用 screenshot-enabled");
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private String toAbsoluteUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return shareUrlResolver.buildShareUrl(url);
    }
}
