<template>
  <div class="codegen-page">
    <a-row :gutter="24">
      <a-col :span="10">
        <a-card title="需求描述">
          <a-textarea
            v-model:value="prompt"
            placeholder="请描述你的代码生成需求，例如：创建一个 Spring Boot REST API，包含用户 CRUD 功能..."
            :rows="12"
            :maxlength="5000"
            show-count
          />
          <div class="actions">
            <a-button type="primary" :loading="generating" @click="handleGenerate">
              流式生成
            </a-button>
            <a-button :loading="generating" @click="handleGenerateSync">
              同步生成
            </a-button>
            <a-button @click="handleClear">清空</a-button>
          </div>
        </a-card>
      </a-col>
      <a-col :span="14">
        <a-card title="生成结果">
          <div class="result-area">
            <pre v-if="result">{{ result }}</pre>
            <a-empty v-else description="生成结果将在这里显示" />
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { generateCode, generateCodeStream } from '@/api/codegen'

const prompt = ref('')
const result = ref('')
const generating = ref(false)

async function handleGenerate() {
  if (!prompt.value.trim()) {
    message.warning('请输入需求描述')
    return
  }
  generating.value = true
  result.value = ''
  try {
    const response = await generateCodeStream({ prompt: prompt.value })
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    if (!reader) throw new Error('无法读取流式响应')

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      result.value += decoder.decode(value, { stream: true })
    }
  } catch (e: any) {
    message.error(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

async function handleGenerateSync() {
  if (!prompt.value.trim()) {
    message.warning('请输入需求描述')
    return
  }
  generating.value = true
  try {
    const res = await generateCode({ prompt: prompt.value })
    result.value = res.data
  } catch (e: any) {
    message.error(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

function handleClear() {
  prompt.value = ''
  result.value = ''
}
</script>

<style scoped>
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

.result-area {
  min-height: 400px;
  max-height: 600px;
  overflow: auto;
}

.result-area pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
