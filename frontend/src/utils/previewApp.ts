import type { CodeFile } from '@/types/codegen'
import { updateAppCode } from '@/api/app'
import { getAppPreview } from '@/api/appDeploy'
import { toBackendAssetUrl } from '@/utils/assetUrl'

export function serializeCodeFiles(files: CodeFile[]): string {
  if (files.length === 1 && (files[0].path === 'index.html' || files[0].path === 'preview.html')) {
    return files[0].content
  }
  return JSON.stringify(files)
}

function simpleHash(text: string): string {
  let hash = 0
  for (let i = 0; i < text.length; i++) {
    hash = ((hash << 5) - hash + text.charCodeAt(i)) | 0
  }
  return hash.toString(36)
}

export function hashCodeFiles(files: CodeFile[]): string {
  return (files ?? [])
    .map(f => `${f.path}:${simpleHash(f.content ?? '')}`)
    .sort()
    .join('|')
}

interface PreviewCacheEntry {
  hash: string
  baseUrl: string
}

const previewCache = new Map<number, PreviewCacheEntry>()

export function clearPreviewCache(appId?: number) {
  if (appId == null) {
    previewCache.clear()
    return
  }
  previewCache.delete(appId)
}

export interface SyncPreviewOptions {
  /** 忽略内容 hash，强制重新同步 */
  force?: boolean
}

export async function syncFilesAndGetPreviewUrl(
  appId: number,
  files: CodeFile[],
  options?: SyncPreviewOptions,
): Promise<string> {
  const safeFiles = files ?? []
  if (!safeFiles.length) {
    throw new Error('没有可预览的代码文件')
  }

  const hash = hashCodeFiles(safeFiles)
  const cached = previewCache.get(appId)

  if (!options?.force && cached?.hash === hash && cached.baseUrl) {
    return `${cached.baseUrl}?v=${Date.now()}`
  }

  await updateAppCode(appId, { codeContent: serializeCodeFiles(safeFiles) })
  const res = await getAppPreview(appId)
  if (!res?.data?.previewUrl) {
    throw new Error('预览地址获取失败，请稍后重试')
  }

  const base = toBackendAssetUrl(res.data.previewUrl)
  previewCache.set(appId, { hash, baseUrl: base.split('?')[0] })
  return `${base.split('?')[0]}?v=${Date.now()}`
}
