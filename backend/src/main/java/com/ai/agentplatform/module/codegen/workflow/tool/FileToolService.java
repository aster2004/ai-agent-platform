package com.ai.agentplatform.module.codegen.workflow.tool;

import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * 文件创建工具服务，暴露给 LLM 的 createFile / writeFile 工具。
 * <p>
 * 使用 ThreadLocal 隔离每个请求的文件映射，避免多并发请求串数据。
 * 支持注册 fileCreated 回调，用于 SSE 实时通知前端文件创建进度。
 * </p>
 */
@Component
public class FileToolService {

    private final ThreadLocal<LinkedHashMap<String, String>> files =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private final ThreadLocal<Consumer<String>> fileCreatedCallback = new ThreadLocal<>();

    /** 重置当前线程的文件缓存（每次生成前调用） */
    public void reset() {
        files.get().clear();
    }

    /** 注册文件创建回调（SSE 流式推送用） */
    public void setFileCreatedCallback(Consumer<String> callback) {
        fileCreatedCallback.set(callback);
    }

    /** 清除回调 */
    public void clearFileCreatedCallback() {
        fileCreatedCallback.remove();
    }

    @Tool("创建或覆盖项目文件，用于生成任何类型的代码文件")
    public String createFile(
            @P("文件相对路径，如 src/main.py 或 index.html") String path,
            @P("文件完整内容") String content) {
        files.get().put(path, content);
        Consumer<String> cb = fileCreatedCallback.get();
        if (cb != null) {
            cb.accept(path);
        }
        return "已创建文件: " + path;
    }

    @Tool("向已有文件追加内容")
    public String writeFile(
            @P("文件相对路径") String path,
            @P("要追加的内容") String content) {
        files.get().merge(path, content, String::concat);
        return "已写入文件: " + path;
    }

    /** 快照当前线程的所有文件 */
    public List<CodeFile> snapshotFiles() {
        List<CodeFile> result = new ArrayList<>();
        files.get().forEach((path, content) -> result.add(new CodeFile(path, content)));
        return result;
    }
}