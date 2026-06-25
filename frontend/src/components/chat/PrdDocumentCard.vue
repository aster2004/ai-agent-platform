<template>
  <div class="prd-card" @click="$emit('open')">
    <div class="prd-icon">
      <FileTextOutlined />
    </div>
    <div class="prd-info">
      <span class="prd-name">需求文档.md</span>
      <span class="prd-hint">点击编辑需求文档</span>
    </div>
    <DownloadOutlined class="prd-download" @click.stop="$emit('download')" />
  </div>
</template>

<script setup lang="ts">
import { FileTextOutlined, DownloadOutlined } from '@ant-design/icons-vue'

defineEmits<{
  open: []
  download: []
}>()
</script>

<style scoped>
.prd-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin: 12px 0;
  border-radius: 10px;
  border: 1px solid #e8e8e8;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.prd-card:hover {
  border-color: #1677ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.1);
}

.prd-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #e6f4ff;
  color: #1677ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.prd-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.prd-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.prd-hint {
  font-size: 12px;
  color: #999;
}

.prd-download {
  font-size: 16px;
  color: #999;
  padding: 4px;
}

.prd-download:hover {
  color: #1677ff;
}
</style>
