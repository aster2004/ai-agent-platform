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
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P2：Selenium 封面截图。需在 application.yml 设置 app.deploy.screenshot-enabled=true，
 * 且本机已安装 Chrome 与对应 ChromeDriver。
 *
 * <p>Selenium Manager 解析 driver 时会启动子进程并在虚拟线程上 waitFor；
 * 若使用 {@code Future.cancel(true)} 中断虚拟线程，会被包装成 NoSuchDriverException。
 * 因此截图任务必须在平台线程上执行，且超时后不可 interrupt。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.deploy.screenshot-enabled", havingValue = "true")
public class CoverScreenshotService {

    /** 首次启动 Chrome + Selenium Manager 解析 driver 可能较慢 */
    private static final long CAPTURE_HARD_TIMEOUT_SECONDS = 90;

    private final AppPreviewService appPreviewService;
    private final AppDeployAccessor appDeployAccessor;
    private final DeployPathResolver deployPathResolver;
    private final ShareUrlResolver shareUrlResolver;

    @Value("${server.port:8080}")
    private int serverPort;

    /** 单平台线程串行截图，避免虚拟线程 interrupt 打断 Selenium Manager */
    private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "cover-screenshot-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

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
        Future<?> future = captureExecutor.submit(() -> runSeleniumCapture(url, outputPath));
        try {
            future.get(CAPTURE_HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 不可 cancel(true)：会 interrupt 平台线程上的 Selenium Manager 子进程 waitFor
            future.cancel(false);
            throw new BusinessException("封面截图超时，请稍后重试或检查 Chrome 是否可用");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw e;
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
            var js = (org.openqa.selenium.JavascriptExecutor) driver;
            Number pageWidth = (Number) js.executeScript(
                    "return Math.max(document.documentElement.scrollWidth, document.body.scrollWidth, 1280)");
            Number pageHeight = (Number) js.executeScript(
                    "return Math.max(document.documentElement.scrollHeight, document.body.scrollHeight, 720)");
            int width = Math.min(Math.max(pageWidth.intValue(), 320), 8192);
            int height = Math.min(Math.max(pageHeight.intValue(), 200), 8192);
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(width, height));
            Thread.sleep(800);
            byte[] screenshot = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            Files.write(outputPath, screenshot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("封面截图被中断: url={}", url, e);
            throw new BusinessException("封面截图被中断，请稍后重试");
        } catch (Exception e) {
            log.error("封面截图失败: url={}", url, e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (detail.contains("NoSuchDriver") || detail.contains("InterruptedException")) {
                throw new BusinessException("封面截图失败：ChromeDriver 初始化被中断，请确认已安装 Chrome 并重试");
            }
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
