<template>
  <div
    class="column-resizer"
    :class="{ active }"
    @mousedown="$emit('start', $event)"
  />
</template>

<script setup lang="ts">
defineProps<{ active?: boolean }>()
defineEmits<{ start: [event: MouseEvent] }>()
</script>

<style scoped>
.column-resizer {
  width: 6px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  position: relative;
  z-index: 2;
}

.column-resizer::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 2px;
  width: 2px;
  background: #eceef2;
  transition: background 0.15s;
}

.column-resizer:hover::after,
.column-resizer.active::after {
  background: #1677ff;
}
</style>
