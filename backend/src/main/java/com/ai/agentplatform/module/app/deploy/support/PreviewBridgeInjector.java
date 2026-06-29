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
                  window.innerWidth || 0,
                  docEl.clientWidth,
                  body.clientWidth,
                  docEl.scrollWidth,
                  body.scrollWidth
                );
                var height = Math.max(
                  window.innerHeight || 0,
                  docEl.clientHeight,
                  body.clientHeight,
                  docEl.scrollHeight,
                  body.scrollHeight
                );
                var MAX_DIM = 8192;
                return {
                  width: Math.min(Math.max(width, 320), MAX_DIM),
                  height: Math.min(Math.max(height, 200), MAX_DIM),
                  viewportWidth: Math.max(window.innerWidth || docEl.clientWidth || width, 320),
                  viewportHeight: Math.max(window.innerHeight || docEl.clientHeight || height, 200)
                };
              }

              function copyLayoutStyles(fromEl, toEl) {
                var cs = window.getComputedStyle(fromEl);
                toEl.style.display = cs.display;
                toEl.style.flexDirection = cs.flexDirection;
                toEl.style.alignItems = cs.alignItems;
                toEl.style.justifyContent = cs.justifyContent;
                toEl.style.flexWrap = cs.flexWrap;
                toEl.style.margin = cs.margin;
                toEl.style.padding = cs.padding;
                toEl.style.background = cs.background;
                toEl.style.backgroundColor = cs.backgroundColor;
                toEl.style.backgroundImage = cs.backgroundImage;
                toEl.style.backgroundSize = cs.backgroundSize;
                toEl.style.backgroundRepeat = cs.backgroundRepeat;
                toEl.style.backgroundPosition = cs.backgroundPosition;
                toEl.style.fontFamily = cs.fontFamily;
                toEl.style.boxSizing = cs.boxSizing;
                toEl.style.position = cs.position;
              }

              function waitForPaint() {
                return new Promise(function (resolve) {
                  requestAnimationFrame(function () {
                    requestAnimationFrame(resolve);
                  });
                });
              }

              function prepareCaptureClone(clonedDoc, size) {
                var clonedBody = clonedDoc.body;
                var clonedHtml = clonedDoc.documentElement;
                if (!clonedBody) {
                  return;
                }
                copyLayoutStyles(document.body, clonedBody);
                clonedBody.style.minHeight = size.height + 'px';
                clonedBody.style.width = size.viewportWidth + 'px';
                clonedBody.style.overflow = 'visible';
                if (clonedHtml) {
                  clonedHtml.style.width = size.viewportWidth + 'px';
                  clonedHtml.style.minHeight = size.height + 'px';
                  clonedHtml.style.height = 'auto';
                  clonedHtml.style.overflow = 'visible';
                }
                var style = clonedDoc.createElement('style');
                style.textContent = [
                  '* { animation: none !important; transition: none !important; }',
                  '.country-card, .result-area, .hint, .country-card *, #resultArea, #resultArea * {',
                  '  opacity: 1 !important; transform: none !important; visibility: visible !important;',
                  '}'
                ].join('\\n');
                if (clonedDoc.head) {
                  clonedDoc.head.appendChild(style);
                }
                var origInputs = document.querySelectorAll('input, textarea, select');
                var clonedInputs = clonedDoc.querySelectorAll('input, textarea, select');
                for (var i = 0; i < origInputs.length && i < clonedInputs.length; i++) {
                  var orig = origInputs[i];
                  var clone = clonedInputs[i];
                  if (orig.tagName === 'INPUT' || orig.tagName === 'TEXTAREA') {
                    clone.value = orig.value;
                    if (orig.tagName === 'INPUT' && clone.type !== 'checkbox' && clone.type !== 'radio') {
                      clone.setAttribute('value', orig.value);
                    }
                  } else if (orig.tagName === 'SELECT') {
                    clone.value = orig.value;
                  }
                }
              }

              function captureCurrentView() {
                return loadHtml2Canvas().then(function (html2canvas) {
                  return waitForPaint().then(function () {
                    var size = measureFullPageSize();
                    var target = document.body || document.documentElement;
                    return html2canvas(target, {
                      width: size.width,
                      height: size.height,
                      windowWidth: size.viewportWidth,
                      windowHeight: size.viewportHeight,
                      scale: 1,
                      useCORS: true,
                      allowTaint: true,
                      logging: false,
                      scrollX: 0,
                      scrollY: 0,
                      backgroundColor: null,
                      onclone: function (clonedDoc) {
                        prepareCaptureClone(clonedDoc, size);
                      }
                    }).then(function (canvas) {
                      return canvas.toDataURL('image/png');
                    });
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
        if (!StringUtils.hasText(html)) {
            return html;
        }
        html = removeExistingBridge(html);
        String lower = html.toLowerCase();
        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + BRIDGE_SCRIPT + html.substring(bodyClose);
        }
        return html + BRIDGE_SCRIPT;
    }

    /** 预览重写时替换旧版桥接脚本，确保截图逻辑可升级 */
    static String removeExistingBridge(String html) {
        int markerIdx = html.indexOf(MARKER);
        if (markerIdx < 0) {
            return html;
        }
        int scriptStart = html.lastIndexOf("<script", markerIdx);
        if (scriptStart < 0) {
            return html.replace(MARKER, "");
        }
        int scriptEnd = html.indexOf("</script>", markerIdx);
        if (scriptEnd < 0) {
            return html;
        }
        return html.substring(0, scriptStart) + html.substring(scriptEnd + "</script>".length());
    }
}
