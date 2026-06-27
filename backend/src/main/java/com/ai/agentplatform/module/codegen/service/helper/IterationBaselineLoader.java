package com.ai.agentplatform.module.codegen.service.helper;

import com.ai.agentplatform.module.chat.entity.ChatMessage;
import com.ai.agentplatform.module.chat.repository.ChatMessageRepository;
import com.ai.agentplatform.module.chat.util.CodeContentDetector;
import com.ai.agentplatform.module.codegen.entity.CodeGenerate;
import com.ai.agentplatform.module.codegen.mapper.CodeGenerateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 迭代生成：加载「当前版本代码」作为 Prompt 基线。
 * 优先级：app.app_code → code_generate 最近成功记录 → 上一轮 ai 消息拼接。
 */
@Slf4j
@Component
public class IterationBaselineLoader {

    private static final int NOT_DELETED = 0;
    private static final int MAX_BASELINE_CHARS = 12000;
    private static final int CHAT_SCAN_LIMIT = 40;

    @Resource
    private AppSyncHelper appSyncHelper;

    @Resource
    private CodeGenerateMapper codeGenerateMapper;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    /**
     * @param sessionId  当前会话
     * @param requestAppId 请求中的 appId（可空）
     */
    public String load(Long sessionId, Long requestAppId) {
        if (sessionId == null) {
            return null;
        }

        Long appId = appSyncHelper.resolveAppIdForSession(sessionId, requestAppId);
        if (appId != null && appId > 0) {
            String appCode = appSyncHelper.getAppCode(appId);
            if (isUsableBaseline(appCode)) {
                log.info("迭代基线来源=app.app_code, sessionId={}, appId={}, chars={}",
                        sessionId, appId, appCode.length());
                return truncate(appCode);
            }
        }

        CodeGenerate latest = codeGenerateMapper.selectLatestSuccessBySessionId(sessionId);
        if (latest != null && isUsableBaseline(latest.getCodeContent())) {
            log.info("迭代基线来源=code_generate, sessionId={}, generateId={}, chars={}",
                    sessionId, latest.getId(), latest.getCodeContent().length());
            return truncate(latest.getCodeContent());
        }

        String fromChat = loadPreviousRoundFromChat(sessionId);
        if (isUsableBaseline(fromChat)) {
            log.info("迭代基线来源=chat_message, sessionId={}, chars={}", sessionId, fromChat.length());
            return truncate(fromChat);
        }

        log.debug("迭代基线未找到, sessionId={}", sessionId);
        return null;
    }

    private String loadPreviousRoundFromChat(Long sessionId) {
        List<ChatMessage> recent = chatMessageRepository.findBySessionIdAndIsDeletedOrderByIdDesc(
                sessionId, NOT_DELETED, PageRequest.of(0, CHAT_SCAN_LIMIT));
        if (recent.isEmpty()) {
            return null;
        }
        List<ChatMessage> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);

        int lastUserIdx = -1;
        for (int i = chronological.size() - 1; i >= 0; i--) {
            if ("user".equals(chronological.get(i).getMessageType())) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx <= 0) {
            return null;
        }

        int prevUserIdx = -1;
        for (int i = lastUserIdx - 1; i >= 0; i--) {
            if ("user".equals(chronological.get(i).getMessageType())) {
                prevUserIdx = i;
                break;
            }
        }
        int start = prevUserIdx < 0 ? 0 : prevUserIdx + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lastUserIdx; i++) {
            ChatMessage msg = chronological.get(i);
            if (!"ai".equals(msg.getMessageType())) {
                continue;
            }
            String content = msg.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(content.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static boolean isUsableBaseline(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String trimmed = content.trim();
        return CodeContentDetector.isCodeOrArtifactContent(trimmed) || trimmed.length() >= 200;
    }

    private static String truncate(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= MAX_BASELINE_CHARS) {
            return content;
        }
        return content.substring(0, MAX_BASELINE_CHARS)
                + "\n\n<!-- 基线代码已截断，仅保留前 " + MAX_BASELINE_CHARS + " 字符 -->";
    }
}
