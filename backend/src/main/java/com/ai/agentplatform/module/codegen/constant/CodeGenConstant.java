package com.ai.agentplatform.module.codegen.constant;

/**
 * 代码生成模块常量
 */
public class CodeGenConstant {
    // SSE 事件名称常量
    public static final String SSE_EVENT_CODE_CHUNK = "code_chunk";
    public static final String SSE_EVENT_FINISH = "finish";
    public static final String SSE_EVENT_ERROR = "error";

    // 生成状态常量
    public static final Integer GENERATE_STATUS_PROCESSING = 0;
    public static final Integer GENERATE_STATUS_SUCCESS = 1;
    public static final Integer GENERATE_STATUS_FAILED = 2;

    // 模型名称常量
    public static final String MODEL_DEEPSEEK = "deepseek";
    public static final String MODEL_OPENAI = "openai";
    public static final String MODEL_BAILIAN = "bailian";
    // 默认兜底模型
    public static final String FALLBACK_MODEL = MODEL_DEEPSEEK;

    // 生成类型常量
    public static final String GENERATE_TYPE_HTML = "HTML";
    public static final String GENERATE_TYPE_VUE = "VUE";
    public static final String GENERATE_TYPE_MULTI_FILE = "MULTI_FILE";
    public static final String GENERATE_TYPE_WORKFLOW = "WORKFLOW";

    // SSE 超时时间（3分钟）
    public static final long SSE_TIMEOUT_MS = 3 * 60 * 1000L;

    // Prompt 最大长度
    public static final int PROMPT_MAX_LENGTH = 5000;

    // ===================== 新增分页、Token、Mock常量 =====================
    // 分页参数限制
    public static final Integer PAGE_MIN_NUM = 1;
    public static final Integer PAGE_MAX_SIZE = 100;
    public static final Integer PAGE_DEFAULT_SIZE = 10;

    // Token 上下限
    public static final Integer TOKEN_MIN = 100;
    public static final Integer TOKEN_MAX = 5000;

    // Mock 默认AppId
    public static final Long MOCK_DEFAULT_APP_ID = 1L;
}