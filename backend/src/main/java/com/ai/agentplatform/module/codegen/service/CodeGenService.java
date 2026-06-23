package com.ai.agentplatform.module.codegen.service;

import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.vo.CodeGenPageVO;
import com.ai.agentplatform.module.codegen.vo.CodeGenVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CodeGenService {
    /** 同步一次性生成代码 */
    CodeGenVO generateSync(CodeGenRequest request);

    /** SSE流式生成代码 */
    SseEmitter generateStream(CodeGenRequest request);

    /** 分页查询个人生成记录 */
    CodeGenPageVO pageRecord(Integer pageNum, Integer pageSize);
}