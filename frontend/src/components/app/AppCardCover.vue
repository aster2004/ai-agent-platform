<template>
  <div class="card-cover">
    <img
      v-if="visible"
      :src="src"
      :alt="alt"
      class="cover-img"
      @error="broken = true"
    />
    <div v-else class="cover-placeholder">
      <AppstoreOutlined />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { AppstoreOutlined } from '@ant-design/icons-vue'
import { resolveCoverUrl } from '@/utils/assetUrl'

const props = defineProps<{
  coverImg?: string
  alt?: string
}>()

const broken = ref(false)

const src = computed(() => {
  const url = resolveCoverUrl(props.coverImg)
  if (!url) return ''
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}v=${encodeURIComponent(props.coverImg || '')}`
})

const visible = computed(() => !!src.value && !broken.value)

watch(
  () => props.coverImg,
  () => {
    broken.value = false
  },
)
</script>

<style scoped>
.card-cover {
  height: 160px;
  overflow: hidden;
  background: #f5f5f5;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 48px;
  color: #bfbfbf;
}
</style>
