package com.ai.agentplatform.module.codegen.service.impl;

import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.mapper.CodeGenerateMapper;
import com.ai.agentplatform.module.codegen.service.CodeGenService;
import com.ai.agentplatform.module.codegen.service.factory.LlmModelFactory;
import com.ai.agentplatform.module.codegen.service.helper.AppSyncHelper;
import com.ai.agentplatform.module.codegen.service.helper.ChatHistoryHelper;
import com.ai.agentplatform.module.codegen.service.tool.PromptBuilder;
import com.ai.agentplatform.module.codegen.service.tool.SseEmitterUtil;
import com.ai.agentplatform.module.codegen.strategy.CodeGenStrategy;
import com.ai.agentplatform.module.codegen.strategy.CodeGenStrategyFactory;
import com.ai.agentplatform.module.codegen.support.CodeGenRequestContext;
import com.ai.agentplatform.module.codegen.util.CurrentUserUtil;
import com.ai.agentplatform.module.codegen.util.ParamFillUtil;
import com.ai.agentplatform.module.codegen.vo.CodeGenPageVO;
import com.ai.agentplatform.module.codegen.vo.CodeGenVO;
import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import com.ai.agentplatform.module.codegen.workflow.tool.FileToolService;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class CodeGenServiceImpl implements CodeGenService {

    @Resource
    private ParamFillUtil paramFillUtil;
    @Resource
    private CurrentUserUtil userUtil;
    @Resource
    private PromptBuilder promptBuilder;
    @Resource
    private LlmModelFactory modelFactory;
    @Resource
    private ChatHistoryHelper chatHelper;
    @Resource
    private AppSyncHelper appSyncHelper;
    @Resource
    private SseEmitterUtil sseUtil;
    @Resource
    private CodeGenerateMapper mapper;
    @Resource
    private CodeGenStrategyFactory strategyFactory;
    @Resource
    private FileToolService fileToolService;
    @Resource(name = "codeGenStreamExecutor")
    private Executor streamExecutor;

    /** 同步生成 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CodeGenVO generateSync(CodeGenRequest request) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Long existingAppId = request.getAppId();
        String realModel = paramFillUtil.fillModel(request.getModelName());
        String realGenerateType = paramFillUtil.fillGenerateType(request.getGenerateType());
        Long sessionId = request.getSessionId();

        // GENERAL 模式走工具驱动生成
        if (CodeGenConstant.GENERATE_TYPE_GENERAL.equals(realGenerateType)) {
            return generateSyncGeneral(request, userId, existingAppId, realModel, sessionId);
        }

        // 原有固定格式路径
        List<String> history = chatHelper.loadChatHistory(sessionId);
        String fullPrompt = promptBuilder.buildPrompt(request, history);

        List<ChatMessage> messageList = new ArrayList<>();
        messageList.add(UserMessage.from(fullPrompt));

        long startTime = System.currentTimeMillis();
        try {
            ChatModel chatModel = modelFactory.getChatModel(realModel);
            ChatResponse chatResponse = chatModel.chat(messageList);
            String aiRawText = chatResponse.aiMessage().text();
            // 通过策略解析多文件/单文件
            CodeGenStrategy strategy = strategyFactory.getStrategy(realGenerateType);
            String fullResult = strategy.parseResult(aiRawText);
            long costTime = System.currentTimeMillis() - startTime;

            Long appId = appSyncHelper.persistGeneratedApp(
                    existingAppId, request.getAppName(), request.getPrompt(), fullResult);

            CodeGenerate record = new CodeGenerate();
            record.setUserId(userId);
            record.setAppId(appId != null ? appId : CodeGenConstant.UNASSIGNED_APP_ID);
            record.setSessionId(sessionId);
            record.setPrompt(request.getPrompt());
            record.setCodeContent(fullResult);
            record.setModelName(realModel);
            record.setGenerateType(realGenerateType);
            record.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
            record.setCostTokens(fullResult.length());
            record.setDuration((int) costTime);
            record.setWorkflowStep("");
            mapper.insert(record);

            CodeGenVO vo = new CodeGenVO();
            vo.setId(record.getId());
            vo.setPrompt(record.getPrompt());
            vo.setCodeContent(fullResult);
            vo.setModelName(realModel);
            vo.setGenerateType(record.getGenerateType());
            vo.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
            vo.setCostTokens(record.getCostTokens());
            vo.setDuration(record.getDuration());
            vo.setCreateTime(record.getCreateTime());
            vo.setErrorMsg(null);
            vo.setWorkflowStep("");
            return vo;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[同步生成失败] userId={}, model={}, generateType={}, 耗时={}ms",
                    userId, realModel, realGenerateType, costTime, e);
            return buildFailRecordAndVo(userId, existingAppId, sessionId, request.getPrompt(),
                    realModel, realGenerateType, e.getMessage(), costTime);
        }
    }

    /** GENERAL 模式同步生成：使用 AiServices + ChatModel + FileToolService 工具驱动 */
    private CodeGenVO generateSyncGeneral(CodeGenRequest request, Long userId, Long existingAppId,
                                          String realModel, Long sessionId) {
        fileToolService.reset();
        long startTime = System.currentTimeMillis();
        try {
            ChatModel chatModel = modelFactory.getChatModel(realModel);
            String fullPrompt = promptBuilder.buildPrompt(request, List.of());

            QuickGenSyncAgent agent = AiServices.builder(QuickGenSyncAgent.class)
                    .chatModel(chatModel)
                    .tools(fileToolService)
                    .build();
            agent.generate(fullPrompt);

            List<CodeFile> files = fileToolService.snapshotFiles();
            String filesJson = JSON.toJSONString(files);
            long costTime = System.currentTimeMillis() - startTime;

            Long appId = appSyncHelper.persistGeneratedApp(
                    existingAppId, request.getAppName(), request.getPrompt(), filesJson);

            CodeGenerate record = new CodeGenerate();
            record.setUserId(userId);
            record.setAppId(appId != null ? appId : CodeGenConstant.UNASSIGNED_APP_ID);
            record.setSessionId(sessionId);
            record.setPrompt(request.getPrompt());
            record.setCodeContent(filesJson);
            record.setModelName(realModel);
            record.setGenerateType(CodeGenConstant.GENERATE_TYPE_GENERAL);
            record.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
            record.setCostTokens(filesJson.length());
            record.setDuration((int) costTime);
            record.setWorkflowStep("");
            mapper.insert(record);

            CodeGenVO vo = new CodeGenVO();
            vo.setId(record.getId());
            vo.setPrompt(record.getPrompt());
            vo.setCodeContent(filesJson);
            vo.setModelName(realModel);
            vo.setGenerateType(record.getGenerateType());
            vo.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
            vo.setCostTokens(record.getCostTokens());
            vo.setDuration(record.getDuration());
            vo.setCreateTime(record.getCreateTime());
            vo.setErrorMsg(null);
            vo.setWorkflowStep("");
            return vo;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[GENERAL同步生成失败] userId={}, model={}, 耗时={}ms", userId, realModel, costTime, e);
            return buildFailRecordAndVo(userId, existingAppId, sessionId, request.getPrompt(),
                    realModel, CodeGenConstant.GENERATE_TYPE_GENERAL, e.getMessage(), costTime);
        }
    }

    /**
     * 构建生成失败记录入库，返回含错误信息的 VO
     */
    private CodeGenVO buildFailRecordAndVo(Long userId, Long existingAppId, Long sessionId,
                                           String prompt, String modelName, String generateType,
                                           String errorMsg, long costTime) {
        CodeGenerate failRecord = new CodeGenerate();
        failRecord.setUserId(userId);
        failRecord.setAppId(existingAppId != null ? existingAppId : CodeGenConstant.UNASSIGNED_APP_ID);
        failRecord.setSessionId(sessionId);
        failRecord.setPrompt(prompt);
        failRecord.setCodeContent(null);
        failRecord.setModelName(modelName);
        failRecord.setGenerateType(generateType);
        failRecord.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_FAILED);
        failRecord.setErrorMsg(truncateErrorMsg(errorMsg));
        failRecord.setDuration((int) costTime);
        failRecord.setWorkflowStep("");
        try {
            mapper.insert(failRecord);
        } catch (Exception dbEx) {
            log.error("[同步生成] 失败记录入库异常", dbEx);
        }

        CodeGenVO failVO = new CodeGenVO();
        failVO.setId(failRecord.getId());
        failVO.setPrompt(prompt);
        failVO.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_FAILED);
        failVO.setErrorMsg(failRecord.getErrorMsg());
        failVO.setDuration(failRecord.getDuration());
        failVO.setModelName(modelName);
        failVO.setGenerateType(generateType);
        failVO.setCreateTime(failRecord.getCreateTime());
        return failVO;
    }

    /**
     * 截断错误信息到数据库字段上限（varchar 500）
     */
    private String truncateErrorMsg(String msg) {
        if (msg == null || msg.isBlank()) {
            return "未知错误";
        }
        return msg.length() > 500 ? msg.substring(0, 497) + "..." : msg;
    }

    /** 流式生成 完全匹配接口三个方法：onNext / onCompleteResponse / onError */
    @Override
    public SseEmitter generateStream(CodeGenRequest request) {
        SseEmitter emitter = sseUtil.createEmitter();
        final String authorization = CodeGenRequestContext.captureAuthorization();
        final Long userId = CurrentUserUtil.getCurrentUserId();
        streamExecutor.execute(() -> CodeGenRequestContext.runWithAuthorization(authorization, () -> {
            try {
                Long existingAppId = request.getAppId();
                String realModel = paramFillUtil.fillModel(request.getModelName());
                String realGenerateType = paramFillUtil.fillGenerateType(request.getGenerateType());
                Long sessionId = request.getSessionId();

                // GENERAL 模式走工具驱动流式生成
                if (CodeGenConstant.GENERATE_TYPE_GENERAL.equals(realGenerateType)) {
                    generateStreamGeneral(emitter, request, userId, existingAppId, realModel, sessionId);
                    return;
                }

                // 原有固定格式流式路径
                List<String> history = chatHelper.loadChatHistory(sessionId);
                String fullPrompt = promptBuilder.buildPrompt(request, history);

                List<ChatMessage> messageList = new ArrayList<>();
                messageList.add(UserMessage.from(fullPrompt));

                StreamingChatModel streamModel = modelFactory.getStreamModel(realModel);
                StringBuilder fullContent = new StringBuilder();
                long startTimestamp = System.currentTimeMillis();

                streamModel.chat(messageList, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        fullContent.append(token);
                        try {
                            sseUtil.sendChunk(emitter, token);
                        } catch (Exception e) {
                            log.error("SSE分片推送异常", e);
                        }
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        CodeGenRequestContext.runWithAuthorization(authorization, () -> {
                            try {
                                long costMs = System.currentTimeMillis() - startTimestamp;
                                String aiRawText = fullContent.toString();
                                CodeGenStrategy strategy = strategyFactory.getStrategy(realGenerateType);
                                String totalText = strategy.parseResult(aiRawText);

                                Long appId = appSyncHelper.persistGeneratedApp(
                                        existingAppId, request.getAppName(), request.getPrompt(), totalText);

                                CodeGenerate record = new CodeGenerate();
                                record.setUserId(userId);
                                record.setAppId(appId != null ? appId : CodeGenConstant.UNASSIGNED_APP_ID);
                                record.setSessionId(sessionId);
                                record.setPrompt(request.getPrompt());
                                record.setCodeContent(totalText);
                                record.setModelName(realModel);
                                record.setGenerateType(realGenerateType);
                                record.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
                                record.setCostTokens(totalText.length());
                                record.setDuration((int) costMs);
                                record.setWorkflowStep("BASIC_CODE_GEN");
                                mapper.insert(record);
                                sseUtil.sendFinish(emitter, totalText);
                            } catch (Exception e) {
                                log.error("流式入库失败", e);
                                try {
                                    sseUtil.sendError(emitter, "数据保存失败：" + e.getMessage());
                                } catch (Exception ignored) {}
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("流式模型调用异常", throwable);
                        try {
                            sseUtil.sendError(emitter, "模型调用失败：" + throwable.getMessage());
                        } catch (Exception ignored) {}
                    }
                });
            } catch (Exception globalEx) {
                log.error("流式整体异常", globalEx);
                try {
                    sseUtil.sendError(emitter, globalEx.getMessage());
                } catch (Exception ignored) {}
            }
        }));
        return emitter;
    }

    /**
     * GENERAL 模式流式生成。
     * 使用与 GenerateNode 相同的可靠模式：AiServices + 阻塞 ChatModel + FileToolService。
     * AI 工具调用期间通过 fileCreatedCallback 实时推送文件创建进度到前端，
     * AI 完成后一次写入记录 + 推送 finish。
     */
    private void generateStreamGeneral(SseEmitter emitter, CodeGenRequest request,
                                       Long userId, Long existingAppId, String realModel,
                                       Long sessionId) {
        fileToolService.reset();
        // 注册回调：每次 AI 调用 createFile 时实时通知前端
        fileToolService.setFileCreatedCallback(path -> {
            try {
                sseUtil.sendFileCreated(emitter, path);
            } catch (IOException e) {
                log.error("SSE file_created 推送异常", e);
            }
        });

        try {
            ChatModel chatModel = modelFactory.getChatModel(realModel);
            String fullPrompt = promptBuilder.buildPrompt(request, List.of());
            long startTimestamp = System.currentTimeMillis();

            // 通知前端 AI 开始工作
            sseUtil.sendChunk(emitter, "正在分析需求并创建文件...\n");

            // 使用与 GenerateNode 相同的可靠模式
            QuickGenSyncAgent agent = AiServices.builder(QuickGenSyncAgent.class)
                    .chatModel(chatModel)
                    .tools(fileToolService)
                    .build();
            agent.generate(fullPrompt);

            long costMs = System.currentTimeMillis() - startTimestamp;
            List<CodeFile> files = fileToolService.snapshotFiles();

            // 兜底：AI 未用工具时，将文本输出作为单文件
            if (files.isEmpty()) {
                CodeFile fallback = new CodeFile("output.txt",
                        "AI 未使用 createFile 工具，请重试或检查模型配置");
                files.add(fallback);
                log.warn("GENERAL 模式 AI 未调用任何工具，返回兜底文件");
            }

            String filesJson = JSON.toJSONString(files);

            Long appId = appSyncHelper.persistGeneratedApp(
                    existingAppId, request.getAppName(), request.getPrompt(), filesJson);

            // 写入数据库记录
            CodeGenerate record = new CodeGenerate();
            record.setUserId(userId);
            record.setAppId(appId != null ? appId : CodeGenConstant.UNASSIGNED_APP_ID);
            record.setSessionId(sessionId);
            record.setPrompt(request.getPrompt());
            record.setCodeContent(filesJson);
            record.setModelName(realModel);
            record.setGenerateType(CodeGenConstant.GENERATE_TYPE_GENERAL);
            record.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
            record.setCostTokens(filesJson.length());
            record.setDuration((int) costMs);
            record.setWorkflowStep("BASIC_CODE_GEN");
            mapper.insert(record);

            sseUtil.sendFinish(emitter, filesJson);
            log.info("GENERAL 流式生成完成, 文件数={}, 耗时={}ms", files.size(), costMs);
        } catch (Exception e) {
            log.error("GENERAL流式生成失败", e);
            // 尝试写入失败记录
            try {
                CodeGenerate failRecord = new CodeGenerate();
                failRecord.setUserId(userId);
                failRecord.setAppId(existingAppId != null ? existingAppId : CodeGenConstant.UNASSIGNED_APP_ID);
                failRecord.setSessionId(sessionId);
                failRecord.setPrompt(request.getPrompt());
                failRecord.setModelName(realModel);
                failRecord.setGenerateType(CodeGenConstant.GENERATE_TYPE_GENERAL);
                failRecord.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_FAILED);
                failRecord.setErrorMsg(truncateErrorMsg(e.getMessage()));
                failRecord.setDuration(0);
                failRecord.setWorkflowStep("");
                mapper.insert(failRecord);
            } catch (Exception dbEx) {
                log.error("GENERAL失败记录入库异常", dbEx);
            }
            try {
                sseUtil.sendError(emitter, "生成失败：" + e.getMessage());
            } catch (Exception ignored) {}
        } finally {
            fileToolService.clearFileCreatedCallback();
        }
    }

    @Override
    public CodeGenPageVO pageRecord(Integer pageNum, Integer pageSize) {
        if(pageNum == null || pageNum < 1) pageNum = 1;
        if(pageSize == null || pageSize < 1 || pageSize > 100) pageSize = 10;
        Long userId = CurrentUserUtil.getCurrentUserId();
        Integer offset = (pageNum - 1) * pageSize;

        List<CodeGenVO> dataList = mapper.selectPage(userId, pageSize, offset);
        Long totalCount = mapper.countByUserId(userId);

        CodeGenPageVO pageVO = new CodeGenPageVO();
        pageVO.setList(dataList);
        pageVO.setTotal(totalCount);
        pageVO.setPageNum(pageNum);
        pageVO.setPageSize(pageSize);
        return pageVO;
    }

    // ===================== GENERAL 模式 AI Agent 接口 =====================

    /** AI Agent：通过 createFile/writeFile 工具自主决定技术栈和文件结构 */
    interface QuickGenSyncAgent {
        @SystemMessage("""
                你是全栈开发专家。根据用户需求自主决定技术栈和项目结构。
                规则：
                1. 必须使用 createFile 工具创建每一个文件，不要直接在回复中输出代码块
                2. 先简要说明项目结构（用文字），然后逐个创建文件
                3. 每个文件调用一次 createFile，传入文件相对路径和完整内容
                4. 支持任何语言和框架：Python、Java、Go、Rust、Node.js、HTML/CSS/JS、Vue、React 等
                5. 如有多文件，确保入口文件（如 index.html、main.py、App.java）最先创建
                6. 代码内容必须完整可运行，不要省略或缩写
                """)
        String generate(@dev.langchain4j.service.UserMessage String userPrompt);
    }
}
