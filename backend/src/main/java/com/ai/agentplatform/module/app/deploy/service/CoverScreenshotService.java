package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import com.ai.agentplatform.module.app.deploy.vo.CoverResultVO;
import com.ai.agentplatform.module.app.deploy.vo.PreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.PageLoadStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * P2：Selenium 封面截图。需在 application.yml 设置 app.deploy.screenshot-enabled=true，
 * 且本机已安装 Chrome 与对应 ChromeDriver。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.deploy.screenshot-enabled", havingValue = "true")
public class CoverScreenshotService {

    private static final long CAPTURE_HARD_TIMEOUT_SECONDS = 30;

    private final AppPreviewService appPreviewService;
    private final AppDeployAccessor appDeployAccessor;
    private final DeployPathResolver deployPathResolver;
    private final ShareUrlResolver shareUrlResolver;

    @Value("${server.port:8080}")
    private int serverPort;

    private final ExecutorService captureExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CoverResultVO captureCover(Long appId) throws IOException, InterruptedException {
        PreviewVO preview = appPreviewService.buildPreview(appId);
        String previewUrl = toLocalScreenshotUrl(preview.getPreviewUrl());

        Path coverFile = deployPathResolver.resolve("covers").resolve(appId + ".png");
        Files.createDirectories(coverFile.getParent());
        captureWithSelenium(previewUrl, coverFile);

        String coverPath = "/covers/" + appId + ".png";
        appDeployAccessor.updateCoverImg(appId, coverPath);
        String coverUrl = shareUrlResolver.buildShareUrl(coverPath);
        return new CoverResultVO(appId, coverUrl);
    }

    private void captureWithSelenium(String url, Path outputPath) throws InterruptedException {
        var future = captureExecutor.submit(() -> runSeleniumCapture(url, outputPath));
        try {
            future.get(CAPTURE_HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BusinessException("封面截图超时，请稍后重试或检查 Chrome 是否可用");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("封面截图失败: " + cause.getMessage());
        }
    }

    private void runSeleniumCapture(String url, Path outputPath) {
        org.openqa.selenium.WebDriver driver = null;
        Path userDataDir = null;
        try {
            userDataDir = Files.createTempDirectory("cover-chrome-");
            var options = new org.openqa.selenium.chrome.ChromeOptions();
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            options.addArguments(
                    "--headless=new",
                    "--window-size=1280,720",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--user-data-dir=" + userDataDir.toAbsolutePath()
            );
            driver = new org.openqa.selenium.chrome.ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(8));
            driver.get(url);
            Thread.sleep(2500);
            byte[] screenshot = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            Files.write(outputPath, screenshot);
        } catch (Exception e) {
            log.error("封面截图失败: url={}", url, e);
            throw new BusinessException("封面截图失败，请确认已安装 Chrome 并启用 screenshot-enabled");
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("关闭 Chrome 失败", e);
                }
            }
            if (userDataDir != null) {
                deleteRecursively(userDataDir);
            }
        }
    }

    private void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    /** 截图始终走本机 127.0.0.1，避免 Headless Chrome 访问局域网 IP 长时间无响应 */
    private String toLocalScreenshotUrl(String url) {
        String path = shareUrlResolver.normalizeToPath(url);
        return shareUrlResolver.buildLocalhostUrl(serverPort, path);
    }
}
