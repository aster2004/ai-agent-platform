package com.ai.agentplatform.module.user.repository;

import com.ai.agentplatform.module.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
