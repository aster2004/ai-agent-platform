package com.ai.agentplatform.module.app.deploy.support;

import org.springframework.util.StringUtils;

/**
 * 向预览 HTML 注入桥接脚本：错误回传、健康检测、Console 转发（供 iframe 父页面展示）。
 */
public final class PreviewBridgeInjector {

    private static final String MARKER = "<!--AI_PREVIEW_BRIDGE-->";

    private static final String BRIDGE_SCRIPT = """
            <script><!--AI_PREVIEW_BRIDGE-->
            (function () {
              var SOURCE = 'ai-agent-preview';
              function post(type, payload) {
                try {
                  if (window.parent && window.parent !== window) {
                    window.parent.postMessage(Object.assign({ source: SOURCE, type: type }, payload || {}), '*');
                  }
                } catch (e) { /* ignore */ }
              }
              window.addEventListener('error', function (e) {
                post('preview-error', {
                  message: e.message || 'Unknown error',
                  filename: e.filename || '',
                  lineno: e.lineno || 0
                });
              });
              window.addEventListener('unhandledrejection', function (e) {
                post('preview-error', { message: String(e.reason || 'Unhandled rejection') });
              });
              ['log', 'warn', 'error'].forEach(function (level) {
                var orig = console[level];
                console[level] = function () {
                  orig.apply(console, arguments);
                  post('preview-console', {
                    level: level,
                    message: Array.prototype.slice.call(arguments).map(String).join(' ')
                  });
                };
              });
              function healthCheck() {
                setTimeout(function () {
                  var app = document.getElementById('app');
                  var body = document.body;
                  if (!body) {
                    post('preview-empty', { reason: 'no-body' });
                    return;
                  }
                  var interactive = body.querySelectorAll(
                    'input, button, form, a, select, textarea, [role="button"]'
                  ).length;
                  var nodes = body.querySelectorAll('*').length;
                  var appEmpty = app && !String(app.innerHTML || '').trim();
                  var tooEmpty = nodes < 6 || (interactive === 0 && nodes < 18);
                  if (appEmpty || tooEmpty) {
                    post('preview-empty', {
                      nodes: nodes,
                      interactive: interactive,
                      appEmpty: !!appEmpty,
                      message: '页面内容过少，可能预览构建失败'
                    });
                  } else {
                    post('preview-ready', { nodes: nodes, interactive: interactive });
                  }
                }, 400);
              }
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', healthCheck);
              } else {
                healthCheck();
              }
            })();
            </script>
            """;

    private PreviewBridgeInjector() {
    }

    public static String inject(String html) {
        if (!StringUtils.hasText(html) || html.contains(MARKER)) {
            return html;
        }
        String lower = html.toLowerCase();
        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + BRIDGE_SCRIPT + html.substring(bodyClose);
        }
        return html + BRIDGE_SCRIPT;
    }
}
