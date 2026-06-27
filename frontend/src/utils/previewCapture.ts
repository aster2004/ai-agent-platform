const PREVIEW_SOURCE = 'ai-agent-preview'
const PREVIEW_WIDTH = 1280
const PREVIEW_HEIGHT = 720

export function requestPreviewCoverCapture(iframe: HTMLIFrameElement, timeoutMs = 45000): Promise<string> {
  const win = iframe.contentWindow
  if (!win) {
    return Promise.reject(new Error('预览窗口未就绪，请先刷新预览'))
  }

  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      window.removeEventListener('message', onMessage)
      reject(new Error('截图超时，请确认预览页已加载完成'))
    }, timeoutMs)

    function onMessage(event: MessageEvent) {
      const data = event.data
      if (!data || data.source !== PREVIEW_SOURCE) return
      if (data.type === 'capture-cover-result' && data.dataUrl) {
        window.clearTimeout(timer)
        window.removeEventListener('message', onMessage)
        resolve(data.dataUrl as string)
      } else if (data.type === 'capture-cover-error') {
        window.clearTimeout(timer)
        window.removeEventListener('message', onMessage)
        reject(new Error(data.message || '截图失败'))
      }
    }

    window.addEventListener('message', onMessage)
    win.postMessage({ type: 'capture-cover-request' }, '*')
  })
}

export async function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  const response = await fetch(dataUrl)
  return response.blob()
}

export function calcPreviewScale(containerWidth: number, containerHeight: number): number {
  const padding = 24
  const availW = Math.max(containerWidth - padding, 200)
  const availH = Math.max(containerHeight - padding, 200)
  return Math.min(availW / PREVIEW_WIDTH, availH / PREVIEW_HEIGHT, 1)
}

export const PREVIEW_VIEWPORT = {
  width: PREVIEW_WIDTH,
  height: PREVIEW_HEIGHT,
}
