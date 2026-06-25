import { onBeforeUnmount, ref } from 'vue'

export interface ColumnWidths {
  chat: number
  tree: number
}

export function useColumnResize(
  containerRef: () => HTMLElement | null,
  initial: ColumnWidths,
  limits: ColumnWidths,
  minPreviewWidth = 320,
) {
  const widths = ref<ColumnWidths>({ ...initial })
  let activeIndex = -1
  let startX = 0
  let startWidths: ColumnWidths = { ...initial }
  const resizerWidth = 6

  function beginResize(index: number, event: MouseEvent) {
    event.preventDefault()
    activeIndex = index
    startX = event.clientX
    startWidths = { ...widths.value }
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }

  function clamp(value: number, min: number, max: number) {
    return Math.min(Math.max(value, min), max)
  }

  function onMouseMove(event: MouseEvent) {
    const container = containerRef()
    if (!container || activeIndex < 0) return

    const delta = event.clientX - startX
    const total = container.clientWidth
    const chrome = resizerWidth * 2

    if (activeIndex === 0) {
      const maxChat = total - startWidths.tree - minPreviewWidth - chrome
      const nextChat = clamp(startWidths.chat + delta, limits.chat, maxChat)
      widths.value = { ...widths.value, chat: nextChat }
      return
    }

    if (activeIndex === 1) {
      const maxTree = total - widths.value.chat - minPreviewWidth - chrome
      const nextTree = clamp(startWidths.tree + delta, limits.tree, maxTree)
      widths.value = { ...widths.value, tree: nextTree }
    }
  }

  function onMouseUp() {
    activeIndex = -1
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  onBeforeUnmount(onMouseUp)

  return { widths, beginResize }
}
