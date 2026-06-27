package com.ai.agentplatform.module.app.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.common.util.SecurityUtils;
import com.ai.agentplatform.module.app.deploy.service.AppCoverAutoGenerateService;
import com.ai.agentplatform.module.app.dto.AppCodeUpdateRequest;
import com.ai.agentplatform.module.app.dto.AppCreateRequest;
import com.ai.agentplatform.module.app.dto.AppUpdateRequest;
import com.ai.agentplatform.module.app.entity.App;
import com.ai.agentplatform.module.app.repository.AppRepository;
import com.ai.agentplatform.module.app.vo.AppVO;
import com.ai.agentplatform.module.chat.entity.ChatSession;
import com.ai.agentplatform.module.chat.repository.ChatSessionRepository;
import com.ai.agentplatform.module.codegen.workflow.repository.CodeGenerateRepository;
import com.ai.agentplatform.module.user.entity.User;
import com.ai.agentplatform.module.user.repository.UserRepository;
import com.ai.agentplatform.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppService {

    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_OFFLINE = "offline";

    private final AppRepository appRepository;
    private final UserRepository userRepository;
    private final CodeGenerateRepository codeGenerateRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AppCoverAutoGenerateService appCoverAutoGenerateService;
    private final UserService userService;

    @Transactional
    public AppVO create(AppCreateRequest request, Long userId) {
        App app = new App();
        app.setAppName(request.getAppName());
        app.setDescription(request.getDescription());
        app.setUserId(userId);
        app.setStatus(STATUS_NORMAL);
        app.setIsFeatured(0);
        App savedApp = appRepository.save(app);
        userService.addPointsWithDailyLimit(userId, 20, UserService.POINT_TYPE_APP_CREATE,
                "创建应用：" + request.getAppName(), 100, savedApp.getId(), "APP");
        return AppVO.from(savedApp);
    }

    public Page<AppVO> listByUser(Long userId, int page, int size) {
        Page<App> appPage = appRepository.findByUserIdAndStatus(userId, STATUS_NORMAL, PageRequest.of(page, size));
        return enrichWithCreatorName(appPage);
    }

    public Page<AppVO> listAllForAdmin(int page, int size, Integer isFeatured) {
        Page<App> appPage = isFeatured == null
                ? appRepository.findAllByOrderByCreateTimeDesc(PageRequest.of(page, size))
                : appRepository.findByIsFeaturedOrderByCreateTimeDesc(isFeatured, PageRequest.of(page, size));
        return enrichWithCreatorName(appPage);
    }

    public List<AppVO> listFeatured() {
        List<App> apps = appRepository.findByIsFeaturedAndStatusOrderByCreateTimeDesc(1, STATUS_NORMAL);
        return enrichListWithCreatorName(apps);
    }

    @Transactional
    public void recordVisit(Long appId, Long visitorUserId) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        if (app.getUserId().equals(visitorUserId)) {
            return;
        }
        if (app.getIsFeatured() == 1) {
            userService.addPointsWithDailyLimit(app.getUserId(), 1, UserService.POINT_TYPE_APP_VISIT,
                    "应用" + app.getAppName() + "被访问", 50, visitorUserId, "VISITOR");
        }
    }

    @Transactional
    public void recordDeploy(Long appId, Long deployerUserId) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        if (app.getUserId().equals(deployerUserId)) {
            return;
        }
        if (app.getIsFeatured() == 1) {
            userService.addPointsWithDailyLimit(app.getUserId(), 5, UserService.POINT_TYPE_APP_FAVORITE,
                    "应用" + app.getAppName() + "被部署", 50, deployerUserId, "DEPLOYER");
        }
    }

    public AppVO getById(Long id) {
        App app = appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        ensureAccessible(app);
        return AppVO.from(app);
    }

    public Long resolveSessionId(Long appId, Long userId) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        ensureAccessible(app);
        if (!app.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("无权访问该应用");
        }

        return codeGenerateRepository.findFirstByAppIdAndSessionIdNotNullOrderByCreateTimeDesc(appId)
                .map(record -> verifyOwnedSession(record.getSessionId(), userId))
                .or(() -> chatSessionRepository
                        .findByUserIdAndAppIdOrderByLastMessageTimeDescCreateTimeDesc(
                                userId, appId, PageRequest.of(0, 1))
                        .stream()
                        .findFirst()
                        .map(ChatSession::getId))
                .or(() -> chatSessionRepository
                        .findByUserIdOrderByLastMessageTimeDescCreateTimeDesc(userId, PageRequest.of(0, 50))
                        .stream()
                        .filter(session -> app.getAppName().equals(session.getSessionTitle()))
                        .findFirst()
                        .map(ChatSession::getId))
                .orElseThrow(() -> new BusinessException("未找到该应用对应的对话"));
    }

    private Long verifyOwnedSession(Long sessionId, Long userId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("未找到该应用对应的对话"));
        if (!session.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("无权访问该对话");
        }
        return session.getId();
    }

    @Transactional
    public AppVO update(Long id, AppUpdateRequest request, Long userId) {
        App app = appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        ensureAccessible(app);
        if (!app.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("无权操作该应用");
        }
        app.setAppName(request.getAppName());
        app.setDescription(request.getDescription());
        if (request.getCoverImg() != null) {
            app.setCoverImg(request.getCoverImg().isBlank() ? null : request.getCoverImg().trim());
        }
        return AppVO.from(appRepository.save(app));
    }

    @Transactional
    public AppVO setFeatured(Long id, boolean featured) {
        App app = appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        if (STATUS_OFFLINE.equals(app.getStatus())) {
            throw new BusinessException("已下架应用不能设为精选");
        }
        boolean wasFeatured = app.getIsFeatured() == 1;
        app.setIsFeatured(featured ? 1 : 0);
        App savedApp = appRepository.save(app);
        if (featured && !wasFeatured) {
            userService.addPointsWithDailyLimit(app.getUserId(), 50, UserService.POINT_TYPE_APP_FEATURED,
                    "应用" + app.getAppName() + "被设为精选", 50);
        }
        return AppVO.from(savedApp);
    }

    @Transactional
    public AppVO updateCode(Long id, AppCodeUpdateRequest request) {
        App app = appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        ensureAccessible(app);
        app.setAppCode(request.getCodeContent());
        AppVO vo = AppVO.from(appRepository.save(app));
        scheduleCoverGenerationAfterCommit(id);
        return vo;
    }

    private void scheduleCoverGenerationAfterCommit(Long appId) {
        Runnable task = () -> appCoverAutoGenerateService.scheduleIfMissing(appId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    @Transactional
    public void delete(Long id, Long userId) {
        App app;
        if (SecurityUtils.isAdmin()) {
            app = appRepository.findById(id)
                    .filter(a -> STATUS_NORMAL.equals(a.getStatus()))
                    .orElseThrow(() -> new BusinessException("应用不存在或无权操作"));
        } else {
            app = appRepository.findByIdAndUserIdAndStatus(id, userId, STATUS_NORMAL)
                    .orElseThrow(() -> new BusinessException("应用不存在或无权操作"));
        }
        app.setStatus(STATUS_OFFLINE);
        appRepository.save(app);
    }

    private Page<AppVO> enrichWithCreatorName(Page<App> appPage) {
        Map<Long, String> creatorNames = loadCreatorNames(appPage.getContent());
        return appPage.map(app -> {
            AppVO vo = AppVO.from(app);
            vo.setCreatorName(creatorNames.get(app.getUserId()));
            return vo;
        });
    }

    private List<AppVO> enrichListWithCreatorName(List<App> apps) {
        Map<Long, String> creatorNames = loadCreatorNames(apps);
        return apps.stream().map(app -> {
            AppVO vo = AppVO.from(app);
            vo.setCreatorName(creatorNames.get(app.getUserId()));
            return vo;
        }).toList();
    }

    private Map<Long, String> loadCreatorNames(List<App> apps) {
        Set<Long> userIds = apps.stream()
                .map(App::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::resolveDisplayName, (a, b) -> a));
    }

    private String resolveDisplayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private void ensureAccessible(App app) {
        if (STATUS_OFFLINE.equals(app.getStatus())) {
            throw new BusinessException("应用不存在");
        }
    }
}
