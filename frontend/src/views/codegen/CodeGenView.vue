<template>
  <div class="codegen-page">
    <!-- ========== 模式切换 (公共壳子，成员4 & 成员5 统一入口) ========== -->
    <a-tabs v-model:activeKey="activeMode" class="mode-tabs" @change="onModeChange">
      <a-tab-pane key="quick" tab="⚡ 快速生成">
        <QuickGenView />
      </a-tab-pane>
      <a-tab-pane key="workflow" tab="🔄 工作流生成">
        <WorkflowView />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
// ==================== 公共壳子：仅负责模式切换 + 路由同步 ====================
// 成员4 改 QuickGenView.vue，成员5 改 WorkflowView.vue，互不影响
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QuickGenView from '@/views/codegen/quick/QuickGenView.vue'
import WorkflowView from '@/views/codegen/workflow/WorkflowView.vue'

const route = useRoute()
const router = useRouter()
const activeMode = ref<'quick' | 'workflow'>(
  (route.query.mode as 'quick' | 'workflow') || 'quick',
)

/** 切换模式时同步到 URL query，支持深链接 */
function onModeChange(mode: string) {
  router.replace({ query: { mode } })
}
</script>

<style scoped>
.codegen-page {
  min-height: 500px;
}

.mode-tabs {
  margin-bottom: 16px;
}

.mode-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
}

.mode-tabs :deep(.ant-tabs-tab) {
  font-size: 15px;
  font-weight: 500;
  padding: 8px 24px;
}
</style>
