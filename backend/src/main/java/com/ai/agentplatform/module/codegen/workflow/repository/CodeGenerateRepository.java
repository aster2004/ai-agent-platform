package com.ai.agentplatform.module.codegen.workflow.repository;

import com.ai.agentplatform.module.codegen.workflow.entity.CodeGenerate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodeGenerateRepository extends JpaRepository<CodeGenerate, Long> {

    Optional<CodeGenerate> findFirstByAppIdAndSessionIdNotNullOrderByCreateTimeDesc(Long appId);
}
