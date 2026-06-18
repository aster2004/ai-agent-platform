package com.ai.agentplatform.module.user.repository;

import com.ai.agentplatform.module.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole(String role, Pageable pageable);

    Page<User> findByStatus(String status, Pageable pageable);
}
