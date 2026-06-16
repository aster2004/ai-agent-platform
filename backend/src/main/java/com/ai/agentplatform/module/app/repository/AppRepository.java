package com.ai.agentplatform.module.app.repository;

import com.ai.agentplatform.module.app.entity.App;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRepository extends JpaRepository<App, Long> {

    Page<App> findByUserId(Long userId, Pageable pageable);
}
