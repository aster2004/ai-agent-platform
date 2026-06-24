package com.ai.agentplatform.module.user.repository;

import com.ai.agentplatform.module.user.entity.UserCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserCheckinRepository extends JpaRepository<UserCheckin, Long> {

    boolean existsByUserIdAndCheckinDate(Long userId, LocalDate checkinDate);

    @Query("SELECT COUNT(c) FROM UserCheckin c WHERE c.userId = :userId AND c.checkinDate >= :startDate")
    long countByUserIdAndCheckinDateAfter(@Param("userId") Long userId, @Param("startDate") LocalDate startDate);

    @Query("SELECT c.checkinDate FROM UserCheckin c WHERE c.userId = :userId ORDER BY c.checkinDate DESC")
    List<LocalDate> findCheckinDatesByUserIdOrderByDateDesc(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM UserCheckin c WHERE c.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
}