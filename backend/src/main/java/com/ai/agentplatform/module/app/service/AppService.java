package com.ai.agentplatform.module.app.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.dto.AppCodeUpdateRequest;
import com.ai.agentplatform.module.app.dto.AppCreateRequest;
import com.ai.agentplatform.module.app.dto.AppUpdateRequest;
import com.ai.agentplatform.module.app.entity.App;
import com.ai.agentplatform.module.app.repository.AppRepository;
import com.ai.agentplatform.module.app.vo.AppVO;
import com.ai.agentplatform.module.user.entity.User;
import com.ai.agentplatform.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public AppVO create(AppCreateRequest request, Long userId) {
        App app = new App();
        app.setAppName(request.getAppName());
        app.setDescription(request.getDescription());
        app.setUserId(userId);
        app.setStatus(STATUS_NORMAL);
        app.setIsFeatured(0);
        return AppVO.from(appRepository.save(app));
    }

    public Page<AppVO> listByUser(Long userId, int page, int size) {
        return appRepository.findByUserIdAndStatus(userId, STATUS_NORMAL, PageRequest.of(page, size))
                .map(AppVO::from);
    }

    public Page<AppVO> listAllForAdmin(int page, int size, Integer isFeatured) {
        Page<App> appPage = isFeatured == null
                ? appRepository.findAllByOrderByCreateTimeDesc(PageRequest.of(page, size))
                : appRepository.findByIsFeaturedOrderByCreateTimeDesc(isFeatured, PageRequest.of(page, size));
        return enrichWithCreatorName(appPage);
    }

    public List<AppVO> listFeatured() {
        return appRepository.findByIsFeaturedAndStatusOrderByCreateTimeDesc(1, STATUS_NORMAL).stream()
                .map(AppVO::from)
                .toList();
    }

    public AppVO getById(Long id) {
        App app = appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        ensureAccessible(app);
        return AppVO.from(app);
    }

    @Transactional
    public AppVO update(Long id, AppUpdateRequest request, Long userId) {
        App app = appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        ensureAccessible(app);
        if (!app.getUserId().equals(userId)) {
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
        app.setIsFeatured(featured ? 1 : 0);
        return AppVO.from(appRepository.save(app));
    }

    @Transactional
    public AppVO updateCode(Long id, AppCodeUpdateRequest request) {
        App app = appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        ensureAccessible(app);
        app.setAppCode(request.getCodeContent());
        return AppVO.from(appRepository.save(app));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        App app = appRepository.findByIdAndUserIdAndStatus(id, userId, STATUS_NORMAL)
                .orElseThrow(() -> new BusinessException("应用不存在或无权操作"));
        app.setStatus(STATUS_OFFLINE);
        appRepository.save(app);
    }

    private Page<AppVO> enrichWithCreatorName(Page<App> appPage) {
        Set<Long> userIds = appPage.getContent().stream()
                .map(App::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> creatorNames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::resolveDisplayName, (a, b) -> a));
        return appPage.map(app -> {
            AppVO vo = AppVO.from(app);
            vo.setCreatorName(creatorNames.get(app.getUserId()));
            return vo;
        });
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
