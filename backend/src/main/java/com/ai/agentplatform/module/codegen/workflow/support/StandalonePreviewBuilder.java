package com.ai.agentplatform.module.codegen.workflow.support;

import com.ai.agentplatform.module.app.deploy.support.PreviewBridgeInjector;
import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StandalonePreviewBuilder {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile(
            "<template>([\\s\\S]*?)</template>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_SETUP_PATTERN = Pattern.compile(
            "<script[^>]*setup[^>]*>([\\s\\S]*?)</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_PATTERN = Pattern.compile(
            "<style[^>]*>([\\s\\S]*?)</style>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_LEVEL_BINDING = Pattern.compile(
            "^(?:const|let|function)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern COMPONENT_TAG_PATTERN = Pattern.compile(
            "<([A-Z][A-Za-z0-9]*)\\b");

    private StandalonePreviewBuilder() {
    }

    public static void appendPreviewFile(List<CodeFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        if (files.stream().anyMatch(f -> "preview.html".equals(f.getPath()))) {
            return;
        }
        buildPreviewHtml(files).ifPresent(html ->
                files.add(new CodeFile("preview.html", PreviewBridgeInjector.inject(html))));
    }

    public static Optional<String> buildPreviewHtml(List<CodeFile> files) {
        if (files == null || files.isEmpty()) {
            return Optional.empty();
        }

        CodeFile appVue = findFile(files, "src/App.vue");
        if (appVue == null) {
            appVue = files.stream()
                    .filter(f -> f.getPath() != null && f.getPath().endsWith("App.vue"))
                    .findFirst()
                    .orElse(null);
        }
        if (appVue != null && appVue.getContent() != null) {
            return Optional.of(buildFromVueProject(appVue.getContent(), files));
        }

        for (CodeFile file : files) {
            if ("index.html".equals(file.getPath())) {
                String content = file.getContent();
                if (content != null && !content.contains("type=\"module\"")) {
                    return Optional.of(content);
                }
            }
        }

        for (CodeFile file : files) {
            if (file.getPath() != null && file.getPath().endsWith(".html")) {
                return Optional.of(file.getContent());
            }
        }

        return Optional.empty();
    }

    private static CodeFile findFile(List<CodeFile> files, String path) {
        return files.stream()
                .filter(f -> path.equals(f.getPath()))
                .findFirst()
                .orElse(null);
    }

    private static String buildFromVueProject(String appVueContent, List<CodeFile> files) {
        String template = extractGroup(TEMPLATE_PATTERN, appVueContent, "<main style=\"padding:24px\">预览加载中</main>");
        String script = extractGroup(SCRIPT_SETUP_PATTERN, appVueContent, "");
        String styles = collectStyles(appVueContent, files);
        String componentsJs = buildComponentRegistry(template, files);
        String setupBody = prepareSetupBody(script);

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>应用预览</title>
                  <script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f5f7fa; }
                    #app { min-height: 100vh; }
                    %s
                  </style>
                </head>
                <body>
                  <div id="app"></div>
                  <script>
                    const { createApp, ref, reactive, computed, watch, onMounted, onUnmounted } = Vue;
                    %s
                    const App = {
                      template: `%s`,
                      components: { %s },
                      setup() {
                        %s
                      }
                    };
                    try {
                      createApp(App).mount('#app');
                    } catch (err) {
                      document.getElementById('app').innerHTML =
                        '<div style="padding:24px;color:#cf1322;font-family:sans-serif">'
                        + '<h3>预览加载失败</h3><p>' + err.message + '</p></div>';
                      console.error(err);
                    }
                  </script>
                </body>
                </html>
                """.formatted(
                escapeForCss(styles),
                componentsJs,
                escapeForJs(template),
                String.join(", ", collectComponentNames(template)),
                setupBody
        );
    }

    private static String collectStyles(String appVueContent, List<CodeFile> files) {
        StringBuilder styles = new StringBuilder(extractGroup(STYLE_PATTERN, appVueContent, ""));
        for (CodeFile file : files) {
            if (file.getPath() != null && file.getPath().endsWith(".vue") && file.getContent() != null) {
                styles.append(extractGroup(STYLE_PATTERN, file.getContent(), ""));
            }
        }
        return styles.toString();
    }

    private static String buildComponentRegistry(String template, List<CodeFile> files) {
        Set<String> names = collectComponentNames(template);
        if (names.isEmpty()) {
            return "";
        }

        Map<String, String> componentTemplates = new LinkedHashMap<>();
        for (CodeFile file : files) {
            if (file.getPath() == null || !file.getPath().endsWith(".vue") || file.getContent() == null) {
                continue;
            }
            String componentName = extractComponentName(file.getPath());
            if (!names.contains(componentName)) {
                continue;
            }
            String childTemplate = extractGroup(TEMPLATE_PATTERN, file.getContent(),
                    "<div style=\"padding:12px;border:1px dashed #ccc;border-radius:8px\">" + componentName + "</div>");
            componentTemplates.put(componentName, childTemplate);
        }

        StringBuilder js = new StringBuilder();
        for (String name : names) {
            String childTemplate = componentTemplates.getOrDefault(name,
                    "<div style=\"padding:12px;color:#999\">[" + name + " 组件预览]</div>");
            js.append("const ").append(name).append(" = { template: `")
                    .append(escapeForJs(childTemplate))
                    .append("` };\n");
        }
        return js.toString();
    }

    private static Set<String> collectComponentNames(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = COMPONENT_TAG_PATTERN.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String extractComponentName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.replace(".vue", "");
    }

    private static String prepareSetupBody(String script) {
        if (script == null || script.isBlank()) {
            return "return {};";
        }

        String cleaned = script.replaceAll("(?m)^import\\s+.*?;\\s*$", "")
                .replaceAll("(?m)^\\s*define(Props|Emits|Expose|Options|Model)\\([^;]*\\);?\\s*$", "")
                .replaceAll("(?m)^\\s*withDefaults\\([^;]*\\);?\\s*$", "")
                .replaceAll("(?m)^\\s*use(Router|Route|Link)\\([^;]*\\);?\\s*$", "")
                .trim();
        Set<String> bindings = new LinkedHashSet<>();
        for (String line : cleaned.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            // 仅收集顶层声明，忽略函数体内的 const/let
            if (line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }
            Matcher matcher = TOP_LEVEL_BINDING.matcher(line.trim());
            if (matcher.find()) {
                String name = matcher.group(1);
                if (!isReserved(name)) {
                    bindings.add(name);
                }
            }
        }

        if (bindings.isEmpty()) {
            return cleaned + "\nreturn {};";
        }

        String returned = bindings.stream().collect(Collectors.joining(", "));
        return cleaned + "\nreturn { " + returned + " };";
    }

    private static boolean isReserved(String name) {
        return Set.of("if", "for", "while", "switch", "return", "function", "const", "let", "var").contains(name);
    }

    private static String extractGroup(Pattern pattern, String content, String fallback) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallback;
    }

    private static String escapeForJs(String text) {
        return text.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${");
    }

    private static String escapeForCss(String text) {
        return text.replace("</style", "<\\/style");
    }
}
