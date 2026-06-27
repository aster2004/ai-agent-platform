import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd, { message } from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import 'ant-design-vue/dist/reset.css'
import './style.css'

// ---- 临时调试：追踪白框来源，确认后删除 ----
const orig = { success: message.success, error: message.error, info: message.info, warning: message.warning, loading: message.loading }
function patch(level: string) {
  return (...args: any[]) => {
    console.log(`%c[MSG:${level}]`,'color:red;font-size:14px;', ...args, new Error().stack)
    return (orig as any)[level](...args)
  }
}
;(message as any).success = patch('success');(message as any).error = patch('error');(message as any).info = patch('info');(message as any).warning = patch('warning');(message as any).loading = patch('loading')

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Antd)
app.mount('#app')
