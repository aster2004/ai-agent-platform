package com.ai.agentplatform.module.codegen.workflow.tool;

import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FileToolService {

    private final Map<String, String> files = new LinkedHashMap<>();

    public void reset() {
        files.clear();
    }

    @Tool("创建或覆盖项目文件，用于生成 Vue 多文件工程")
    public String createFile(
            @P("文件相对路径，如 src/App.vue 或 package.json") String path,
            @P("文件完整内容") String content) {
        files.put(path, content);
        return "已创建文件: " + path;
    }

    @Tool("向已有文件追加内容")
    public String writeFile(
            @P("文件相对路径") String path,
            @P("要追加的内容") String content) {
        files.merge(path, content, String::concat);
        return "已写入文件: " + path;
    }

    public List<CodeFile> snapshotFiles() {
        List<CodeFile> result = new ArrayList<>();
        files.forEach((path, content) -> result.add(new CodeFile(path, content)));
        return result;
    }
}
