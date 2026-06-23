package com.ai.agentplatform.module.codegen.workflow.repository;

import com.ai.agentplatform.module.codegen.workflow.entity.CodeGenerate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeGenerateRepository extends JpaRepository<CodeGenerate, Long> {
}
