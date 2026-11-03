import { createApp } from 'vue'
import { ElDialog,ElInput,ElButton } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/message/style/css'
import App from './App.vue'
import './style.css'
import './silverwolf.css'
createApp(App).use(ElDialog).use(ElInput).use(ElButton).mount('#app')

// Local diagnostics: browser-measured first load and resource bytes, no source or user payloads.
window.addEventListener('load',()=>{
 const n=performance.getEntriesByType('navigation')[0]
 console.info('[sanctuary-load]',JSON.stringify({domContentLoadedMs:Math.round(n?.domContentLoadedEventEnd || 0),
  resources:performance.getEntriesByType('resource').filter(e=>e.name.includes('/assets/')).map(e=>({name:e.name.split('/').pop(),encodedBytes:e.encodedBodySize,transferBytes:e.transferSize,durationMs:Math.round(e.duration)}))}))
},{once:true})
