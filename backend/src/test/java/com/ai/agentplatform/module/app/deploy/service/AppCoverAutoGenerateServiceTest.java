package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.support.DeployPathResolver;
import com.ai.agentplatform.module.app.entity.App;
import com.ai.agentplatform.module.app.repository.AppRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppCoverAutoGenerateServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private AppRepository appRepository;

    @Mock
    private AppDeployAccessor appDeployAccessor;

    @Mock
    private DeployPathResolver deployPathResolver;

    @Mock
    private ObjectProvider<CoverScreenshotService> coverScreenshotServiceProvider;

    @InjectMocks
    private AppCoverAutoGenerateService appCoverAutoGenerateService;

    @Test
    void shouldSkipWhenCoverAlreadyExists() {
        App app = new App();
        app.setId(1L);
        app.setCoverImg("/covers/1.png");
        when(appRepository.findById(1L)).thenReturn(Optional.of(app));

        appCoverAutoGenerateService.scheduleIfMissing(1L);

        verify(coverScreenshotServiceProvider, never()).getIfAvailable();
        verify(appDeployAccessor, never()).updateCoverImg(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldGenerateFallbackCoverWhenScreenshotDisabled() throws Exception {
        App app = new App();
        app.setId(8L);
        app.setAppName("给我做一个计算器");
        when(appRepository.findById(8L)).thenReturn(Optional.of(app));
        when(coverScreenshotServiceProvider.getIfAvailable()).thenReturn(null);
        when(deployPathResolver.resolve("covers")).thenReturn(tempDir.resolve("covers"));

        appCoverAutoGenerateService.generateFallbackCover(8L);

        assertTrue(tempDir.resolve("covers").resolve("8.png").toFile().exists());
        ArgumentCaptor<String> coverCaptor = ArgumentCaptor.forClass(String.class);
        verify(appDeployAccessor).updateCoverImg(org.mockito.ArgumentMatchers.eq(8L), coverCaptor.capture());
        assertEquals("/covers/8.png", coverCaptor.getValue());
    }
}
