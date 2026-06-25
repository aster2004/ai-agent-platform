package com.ai.agentplatform.module.user.repository;

import com.ai.agentplatform.module.user.entity.UserPointsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPointsLogRepository extends JpaRepository<UserPointsLog, Long> {

    boolean existsByUserIdAndType(Long userId, String type);

    @Query("SELECT DISTINCT l.type FROM UserPointsLog l WHERE l.userId = :userId AND l.type IN (:types)")
    List<String> findCompletedTaskTypes(@Param("userId") Long userId, @Param("types") List<String> types);

    List<UserPointsLog> findByUserIdOrderByCreateTimeDesc(Long userId);
}