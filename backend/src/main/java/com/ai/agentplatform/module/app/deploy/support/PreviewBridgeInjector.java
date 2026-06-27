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

              function loadHtml2Canvas() {
                if (window.html2canvas) {
                  return Promise.resolve(window.html2canvas);
                }
                return new Promise(function (resolve, reject) {
                  var script = document.createElement('script');
                  script.src = 'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js';
                  script.onload = function () { resolve(window.html2canvas); };
                  script.onerror = function () { reject(new Error('html2canvas 加载失败')); };
                  document.head.appendChild(script);
                });
              }

              function measureFullPageSize() {
                var docEl = document.documentElement;
                var body = document.body || docEl;
                var width = Math.max(
                  docEl.scrollWidth, docEl.offsetWidth, docEl.clientWidth,
                  body.scrollWidth, body.offsetWidth, body.clientWidth
                );
                var height = Math.max(
                  docEl.scrollHeight, docEl.offsetHeight, docEl.clientHeight,
                  body.scrollHeight, body.offsetHeight, body.clientHeight
                );
                var MAX_DIM = 8192;
                return {
                  width: Math.min(Math.max(width, 320), MAX_DIM),
                  height: Math.min(Math.max(height, 200), MAX_DIM)
                };
              }

              function captureCurrentView() {
                return loadHtml2Canvas().then(function (html2canvas) {
                  var size = measureFullPageSize();
                  var target = document.body || document.documentElement;
                  return html2canvas(target, {
                    width: size.width,
                    height: size.height,
                    scale: 1,
                    useCORS: true,
                    allowTaint: true,
                    logging: false,
                    scrollX: 0,
                    scrollY: 0,
                    x: 0,
                    y: 0,
                    backgroundColor: '#ffffff'
                  }).then(function (canvas) {
                    return canvas.toDataURL('image/png');
                  });
                });
              }

              window.addEventListener('message', function (event) {
                var data = event.data;
                if (!data || data.type !== 'capture-cover-request') {
                  return;
                }
                captureCurrentView()
                  .then(function (dataUrl) {
                    post('capture-cover-result', { dataUrl: dataUrl });
                  })
                  .catch(function (err) {
                    post('capture-cover-error', { message: err && err.message ? err.message : '截图失败' });
                  });
              });
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
