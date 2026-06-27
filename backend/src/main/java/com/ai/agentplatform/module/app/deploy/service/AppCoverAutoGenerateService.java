package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import com.ai.agentplatform.module.app.entity.App;
import com.ai.agentplatform.module.app.repository.AppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 应用代码写入后自动生成封面：优先 Selenium 截取预览页，超时或失败时生成文字占位图。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppCoverAutoGenerateService {

    private static final int COVER_WIDTH = 1280;
    private static final int COVER_HEIGHT = 720;
    /** 需大于 CoverScreenshotService 内部超时，并留出 Selenium Manager 首次解析 driver 的时间 */
    private static final long SCREENSHOT_TIMEOUT_SECONDS = 120;

    private final AppRepository appRepository;
    private final AppDeployAccessor appDeployAccessor;
    private final DeployPathResolver deployPathResolver;
    private final ObjectProvider<CoverScreenshotService> coverScreenshotServiceProvider;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    /** 平台线程执行截图，避免 virtual thread + cancel(true) 打断 Selenium Manager */
    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cover-auto-screenshot");
        thread.setDaemon(true);
        return thread;
    });
    /** 串行截图，避免多个 Chrome 同时启动导致卡死 */
    private final Semaphore screenshotLock = new Semaphore(1);

    public void scheduleIfMissing(Long appId) {
        if (appId == null || appId <= 0) {
            return;
        }
        appRepository.findById(appId).ifPresent(app -> {
            if (StringUtils.hasText(app.getCoverImg())) {
                log.debug("[封面自动生成] appId={} 已有封面，跳过", appId);
                return;
            }
            if (syncCoverFromDisk(appId)) {
                log.info("[封面自动生成] appId={} 已从磁盘同步封面", appId);
                return;
            }
            executor.execute(() -> generate(appId));
        });
    }

    private boolean syncCoverFromDisk(Long appId) {
        Path coverFile = deployPathResolver.resolve("covers").resolve(appId + ".png");
        if (!Files.isRegularFile(coverFile)) {
            return false;
        }
        appDeployAccessor.updateCoverImg(appId, "/covers/" + appId + ".png");
        return true;
    }

    private void generate(Long appId) {
        try {
            tryScreenshotCapture(appId);
        } finally {
            if (!hasCover(appId)) {
                try {
                    generateFallbackCover(appId);
                    log.info("[封面自动生成] appId={} 占位封面已生成", appId);
                } catch (Exception e) {
                    log.error("[封面自动生成] appId={} 占位封面失败", appId, e);
                }
            } else {
                log.info("[封面自动生成] appId={} 封面已就绪", appId);
            }
        }
    }

    private void tryScreenshotCapture(Long appId) {
        CoverScreenshotService screenshotService = coverScreenshotServiceProvider.getIfAvailable();
        if (screenshotService == null) {
            return;
        }

        boolean acquired = false;
        Future<?> future = null;
        try {
            acquired = screenshotLock.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[封面自动生成] appId={} 截图队列繁忙，跳过 Selenium", appId);
                return;
            }

            CoverScreenshotService service = screenshotService;
            future = screenshotExecutor.submit(() -> {
                try {
                    service.captureCover(appId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            future.get(SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(false);
            }
            log.warn("[封面自动生成] appId={} Selenium 超时 ({}s)，改用占位图", appId, SCREENSHOT_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("[封面自动生成] appId={} Selenium 截图失败，尝试占位图: {}", appId, e.getMessage());
        } finally {
            if (acquired) {
                screenshotLock.release();
            }
        }
    }

    private boolean hasCover(Long appId) {
        return appRepository.findById(appId)
                .map(app -> StringUtils.hasText(app.getCoverImg()))
                .orElse(false);
    }

    void generateFallbackCover(Long appId) throws IOException {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + appId));
        if (StringUtils.hasText(app.getCoverImg())) {
            return;
        }

        Path coverFile = deployPathResolver.resolve("covers").resolve(appId + ".png");
        Files.createDirectories(coverFile.getParent());

        BufferedImage image = new BufferedImage(COVER_WIDTH, COVER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setPaint(new GradientPaint(0, 0, new Color(79, 70, 229), COVER_WIDTH, COVER_HEIGHT, new Color(124, 58, 237)));
            graphics.fillRect(0, 0, COVER_WIDTH, COVER_HEIGHT);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.setFont(resolveTitleFont());

            String title = StringUtils.hasText(app.getAppName()) ? app.getAppName().trim() : "AI 应用";
            if (title.length() > 24) {
                title = title.substring(0, 24) + "...";
            }
            FontMetrics metrics = graphics.getFontMetrics();
            int x = Math.max(32, (COVER_WIDTH - metrics.stringWidth(title)) / 2);
            int y = (COVER_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;
            graphics.drawString(title, x, y);
        } finally {
            graphics.dispose();
        }

        ImageIO.write(image, "png", coverFile.toFile());
        appDeployAccessor.updateCoverImg(appId, "/covers/" + appId + ".png");
    }

    private Font resolveTitleFont() {
        return new Font("Microsoft YaHei", Font.BOLD, 52);
    }
}
