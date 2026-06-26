package com.ai.agentplatform.module.codegen.workflow.service;

import com.ai.agentplatform.module.codegen.workflow.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.workflow.repository.CodeGenerateRepository;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作流记录持久化：虚拟线程中调用 JPA 需独立事务边界。
 */
@Service
@RequiredArgsConstructor
public class WorkflowRecordPersistence {

    private final CodeGenerateRepository codeGenerateRepository;

    @Transactional
    public CodeGenerate save(CodeGenerate record) {
        return codeGenerateRepository.save(record);
    }

    @Transactional
    public void persistAwaitState(CodeGenerate record, String codeContent, Integer durationMs) {
        record.setCodeContent(codeContent);
        record.setWorkflowStep(WorkflowStep.PRD_READY.getCode());
        record.setGenerateStatus(0);
        if (durationMs != null) {
            record.setDuration(durationMs);
        }
        codeGenerateRepository.save(record);
    }
}
