package com.ai.agentplatform.module.app.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.dto.AppCreateRequest;
import com.ai.agentplatform.module.app.entity.App;
import com.ai.agentplatform.module.app.repository.AppRepository;
import com.ai.agentplatform.module.app.vo.AppVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppService {

    private final AppRepository appRepository;

    @Transactional
    public AppVO create(AppCreateRequest request, Long userId) {
        App app = new App();
        app.setAppName(request.getAppName());
        app.setDescription(request.getDescription());
        app.setUserId(userId);
        return AppVO.from(appRepository.save(app));
    }

    public Page<AppVO> listByUser(Long userId, int page, int size) {
        return appRepository.findByUserId(userId, PageRequest.of(page, size))
                .map(AppVO::from);
    }

    public AppVO getById(Long id) {
        return AppVO.from(appRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在")));
    }

    @Transactional
    public void delete(Long id) {
        if (!appRepository.existsById(id)) {
            throw new BusinessException("应用不存在");
        }
        appRepository.deleteById(id);
    }
}
