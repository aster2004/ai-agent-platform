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
import com.ai.agentplatform.module.codegen.util.CurrentUserUtil;
import com.ai.agentplatform.module.codegen.util.ParamFillUtil;
import com.ai.agentplatform.module.codegen.vo.CodeGenPageVO;
import com.ai.agentplatform.module.codegen.vo.CodeGenVO;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.ArrayList;
import java.util.List;

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

    /** 同步生成 修复类型不匹配：接收 ChatResponse */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CodeGenVO generateSync(CodeGenRequest request) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Long realAppId = paramFillUtil.fillAppId(request.getAppId());
        String realModel = paramFillUtil.fillModel(request.getModelName());
        Long sessionId = request.getSessionId();

        List<String> history = chatHelper.loadChatHistory(sessionId);
        String fullPrompt = promptBuilder.buildPrompt(request.getPrompt(), request.getGenerateType(), realAppId, history);

        List<ChatMessage> messageList = new ArrayList<>();
        messageList.add(UserMessage.from(fullPrompt));

        long startTime = System.currentTimeMillis();
        ChatModel chatModel = modelFactory.getChatModel(realModel);
        // 修复：chat返回 ChatResponse，不再用 Response<AiMessage>
        ChatResponse chatResponse = chatModel.chat(messageList);
        String fullResult = chatResponse.aiMessage().text();
        long costTime = System.currentTimeMillis() - startTime;

        CodeGenerate record = new CodeGenerate();
        record.setUserId(userId);
        record.setAppId(realAppId);
        record.setSessionId(sessionId);
        record.setPrompt(request.getPrompt());
        record.setCodeContent(fullResult);
        record.setModelName(realModel);
        record.setGenerateType(request.getGenerateType());
        record.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
        record.setCostTokens(fullResult.length());
        record.setDuration((int) costTime);
        record.setWorkflowStep("");
        mapper.insert(record);
        appSyncHelper.syncCodeToApp(realAppId, fullResult);

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
    }

    /** 流式生成 完全匹配接口三个方法：onNext / onCompleteResponse / onError */
    @Override
    public SseEmitter generateStream(CodeGenRequest request) {
        SseEmitter emitter = sseUtil.createEmitter();
        new Thread(() -> {
            try {
                Long userId = CurrentUserUtil.getCurrentUserId();
                Long realAppId = paramFillUtil.fillAppId(request.getAppId());
                String realModel = paramFillUtil.fillModel(request.getModelName());
                Long sessionId = request.getSessionId();
                List<String> history = chatHelper.loadChatHistory(sessionId);
                String fullPrompt = promptBuilder.buildPrompt(request.getPrompt(), request.getGenerateType(), realAppId, history);

                List<ChatMessage> messageList = new ArrayList<>();
                messageList.add(UserMessage.from(fullPrompt));

                StreamingChatModel streamModel = modelFactory.getStreamModel(realModel);
                StringBuilder fullContent = new StringBuilder();
                long startTimestamp = System.currentTimeMillis();

                streamModel.chat(messageList, new StreamingChatResponseHandler() {
                    // 分片方法：正确名称 onPartialResponse
                    @Override
                    public void onPartialResponse(String token) {
                        fullContent.append(token);
                        try {
                            sseUtil.sendChunk(emitter, token);
                        } catch (Exception e) {
                            log.error("SSE分片推送异常", e);
                        }
                    }

                    // 完成回调
                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        try {
                            long costMs = System.currentTimeMillis() - startTimestamp;
                            String totalText = fullContent.toString();
                            CodeGenerate record = new CodeGenerate();
                            record.setUserId(userId);
                            record.setAppId(realAppId);
                            record.setSessionId(sessionId);
                            record.setPrompt(request.getPrompt());
                            record.setCodeContent(totalText);
                            record.setModelName(realModel);
                            record.setGenerateType(request.getGenerateType());
                            record.setGenerateStatus(CodeGenConstant.GENERATE_STATUS_SUCCESS);
                            record.setCostTokens(totalText.length());
                            record.setDuration((int) costMs);
                            record.setWorkflowStep("");
                            mapper.insert(record);
                            appSyncHelper.syncCodeToApp(realAppId, totalText);
                            sseUtil.sendFinish(emitter);
                        } catch (Exception e) {
                            log.error("流式入库失败", e);
                            try {
                                sseUtil.sendError(emitter, "数据保存失败：" + e.getMessage());
                            } catch (Exception ignored) {}
                        }
                    }

                    // 异常回调
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
        }).start();
        return emitter;
    }

    @Override
    public CodeGenPageVO pageRecord(Integer pageNum, Integer pageSize) {
        // 1. 参数校验：防止非法分页参数
        if(pageNum == null || pageNum < 1) pageNum = 1;
        if(pageSize == null || pageSize < 1 || pageSize > 100) pageSize = 10;
        Long userId = CurrentUserUtil.getCurrentUserId();
        // 2. 计算分页偏移量
        Integer offset = (pageNum - 1) * pageSize;

        // 3. 查询数据：传入offset和pageSize
        List<CodeGenVO> dataList = mapper.selectPage(userId, pageSize, offset);
        Long totalCount = mapper.countByUserId(userId);

        // 4. 封装分页VO
        CodeGenPageVO pageVO = new CodeGenPageVO();
        pageVO.setList(dataList);
        pageVO.setTotal(totalCount);
        pageVO.setPageNum(pageNum);
        pageVO.setPageSize(pageSize);
        return pageVO;
    }
}