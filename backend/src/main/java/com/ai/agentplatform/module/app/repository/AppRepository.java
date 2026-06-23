package com.ai.agentplatform.module.app.repository;

import com.ai.agentplatform.module.app.entity.App;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppRepository extends JpaRepository<App, Long> {

    Page<App> findByUserId(Long userId, Pageable pageable);

    Page<App> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    Optional<App> findByIdAndUserIdAndStatus(Long id, Long userId, String status);

    List<App> findByIsFeaturedAndStatusOrderByCreateTimeDesc(Integer isFeatured, String status);

    Page<App> findAllByOrderByCreateTimeDesc(Pageable pageable);

    Page<App> findByIsFeaturedOrderByCreateTimeDesc(Integer isFeatured, Pageable pageable);
}
