<template>
  <div class="placeholder-page">
    <a-result
      status="info"
      title="应用管理"
      sub-title="功能开发中，敬请期待"
    >
      <template #extra>
        <a-button type="primary" @click="$router.push('/codegen')">前往代码生成</a-button>
      </template>
    </a-result>
  </div>
</template>

<style scoped>
.placeholder-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
</style>
