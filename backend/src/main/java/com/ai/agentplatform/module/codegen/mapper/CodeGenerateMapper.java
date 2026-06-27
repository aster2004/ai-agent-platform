package com.ai.agentplatform.module.codegen.mapper;

import com.ai.agentplatform.module.codegen.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.vo.CodeGenVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * code_generate表MyBatis Mapper接口
 * 子任务1创建空白骨架，子任务3完善SQL调用
 */
@Mapper
public interface CodeGenerateMapper {
    // 新增生成记录
    int insert(CodeGenerate entity);
    // 分页查询用户生成记录
    List<CodeGenVO> selectPage(
            @Param("userId") Long userId,
            @Param("pageSize") Integer pageSize,
            @Param("offset") Integer offset
    );
    // 查询用户总记录数
    Long countByUserId(@Param("userId") Long userId);

    /** 同会话最近一次成功生成的完整代码（迭代修改基线） */
    CodeGenerate selectLatestSuccessBySessionId(@Param("sessionId") Long sessionId);
}