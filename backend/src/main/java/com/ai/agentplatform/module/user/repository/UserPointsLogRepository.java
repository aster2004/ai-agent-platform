package com.ai.agentplatform.module.user.repository;

import com.ai.agentplatform.module.user.entity.UserPointsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserPointsLogRepository extends JpaRepository<UserPointsLog, Long> {

    boolean existsByUserIdAndType(Long userId, String type);

    @Query("SELECT DISTINCT l.type FROM UserPointsLog l WHERE l.userId = :userId AND l.type IN (:types)")
    List<String> findCompletedTaskTypes(@Param("userId") Long userId, @Param("types") List<String> types);

    List<UserPointsLog> findByUserIdOrderByCreateTimeDesc(Long userId);

    @Query("SELECT COALESCE(SUM(l.points), 0) FROM UserPointsLog l WHERE l.userId = :userId AND l.type = :type AND l.recordDate = :date")
    Integer sumPointsByUserIdAndTypeAndDate(@Param("userId") Long userId, @Param("type") String type, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(l.points), 0) FROM UserPointsLog l WHERE l.userId = :userId AND l.recordDate = :date")
    Integer sumPointsByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    List<UserPointsLog> findByUserIdAndRecordDateBetweenOrderByCreateTimeDesc(Long userId, LocalDate startDate, LocalDate endDate);

    List<UserPointsLog> findByUserIdAndRecordDateGreaterThanEqualOrderByCreateTimeDesc(Long userId, LocalDate startDate);

    List<UserPointsLog> findByUserIdAndRecordDateLessThanEqualOrderByCreateTimeDesc(Long userId, LocalDate endDate);

    List<UserPointsLog> findByUserIdAndRecordDateOrderByCreateTimeDesc(Long userId, LocalDate date);

    boolean existsByUserIdAndTypeAndRelatedId(Long userId, String type, Long relatedId);

    boolean existsByUserIdAndTypeAndRelatedIdAndRelatedType(Long userId, String type, Long relatedId, String relatedType);
}