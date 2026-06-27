package com.ai.agentplatform.module.codegen.service.helper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从生成结果中提取文件列表与主入口片段，供记忆摘要 LLM 使用。
 */
public final class CodeFingerprintExtractor {

    private static final int SNIPPET_MAX = 800;
    private static final int FILE_NAME_MAX = 8;
    private static final Pattern MD_FILE = Pattern.compile(
            "##\\s+(?:📁\\s*)?([^\\n]+?)\\s*\\n\\s*```", Pattern.MULTILINE);

    private CodeFingerprintExtractor() {
    }

    public record Fingerprint(List<String> fileNames, String mainSnippet) {
    }

    public static Fingerprint extract(String codeContent) {
        if (codeContent == null || codeContent.isBlank()) {
            return new Fingerprint(List.of(), "");
        }
        String trimmed = codeContent.trim();

        List<FileEntry> files = tryParseJsonFiles(trimmed);
        if (files.isEmpty()) {
            files = tryParseMarkdownFiles(trimmed);
        }
        if (files.isEmpty()) {
            files = List.of(new FileEntry(guessSingleFileName(trimmed), trimmed));
        }

        List<String> names = files.stream()
                .map(FileEntry::path)
                .distinct()
                .limit(FILE_NAME_MAX)
                .toList();
        String snippet = truncate(pickMainEntry(files), SNIPPET_MAX);
        return new Fingerprint(names, snippet);
    }

    public static String describeGenerateType(String generateType) {
        if (generateType == null || generateType.isBlank()) {
            return "代码";
        }
        return switch (generateType.toUpperCase(Locale.ROOT)) {
            case "HTML" -> "HTML 单页";
            case "VUE" -> "Vue 多文件项目";
            case "MULTI_FILE" -> "多文件项目";
            case "WORKFLOW" -> "工作流应用";
            case "GENERAL" -> "通用项目";
            default -> generateType;
        };
    }

    public static String joinFileNames(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return "未知";
        }
        return String.join("、", fileNames);
    }

    private record FileEntry(String path, String content) {
    }

    private static List<FileEntry> tryParseJsonFiles(String text) {
        int start = text.indexOf("[{");
        int end = text.lastIndexOf("}]");
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(text.substring(start, end + 2));
            List<FileEntry> files = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String path = obj.getString("path");
                String content = obj.getString("content");
                if (path != null && content != null && !content.isBlank()) {
                    files.add(new FileEntry(path, content));
                }
            }
            return files;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<FileEntry> tryParseMarkdownFiles(String text) {
        Matcher matcher = MD_FILE.matcher(text);
        List<FileEntry> files = new ArrayList<>();
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            if (!path.contains(".") && !path.contains("/")) {
                continue;
            }
            int codeStart = text.indexOf("```", matcher.end());
            if (codeStart < 0) {
                continue;
            }
            int contentStart = text.indexOf('\n', codeStart);
            if (contentStart < 0) {
                continue;
            }
            contentStart++;
            int codeEnd = text.indexOf("```", contentStart);
            if (codeEnd < 0) {
                continue;
            }
            String code = text.substring(contentStart, codeEnd).trim();
            if (!code.isBlank()) {
                files.add(new FileEntry(path, code));
            }
        }
        return files;
    }

    private static String guessSingleFileName(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("<!doctype") || lower.contains("<html")) {
            return "index.html";
        }
        if (lower.contains("createapp") || lower.contains("<template")) {
            return "App.vue";
        }
        return "output";
    }

    private static String pickMainEntry(List<FileEntry> files) {
        Set<String> priority = new LinkedHashSet<>();
        for (FileEntry file : files) {
            String name = file.path().toLowerCase(Locale.ROOT);
            if (name.endsWith("index.html") || name.equals("index.html")) {
                return file.content();
            }
            if (name.endsWith("app.vue")) {
                priority.add(file.path());
            }
        }
        return files.stream()
                .min(Comparator.comparingInt(f -> mainEntryRank(f.path())))
                .map(FileEntry::content)
                .orElse("");
    }

    private static int mainEntryRank(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("index.html")) {
            return 0;
        }
        if (lower.endsWith("app.vue")) {
            return 1;
        }
        if (lower.endsWith(".html")) {
            return 2;
        }
        if (lower.endsWith(".vue")) {
            return 3;
        }
        return 10;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
