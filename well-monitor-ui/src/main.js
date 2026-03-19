import { createApp } from 'vue'
import App from './App.vue'
// 1. 引入路由配置
import router from './router'

// 引入字体图标和地图的全局 CSS
import '@fortawesome/fontawesome-free/css/all.min.css'
import 'leaflet/dist/leaflet.css'

const app = createApp(App)

// 2. 核心：告诉 Vue 使用路由（必须在 mount 之前！）
app.use(router)

// 3. 挂载到 index.html 的 #app div 上
app.mount('#app')