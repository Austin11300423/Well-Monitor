<template>
  <router-view v-if="route.name === 'login'" />

  <div v-else class="app-container">
    <header>
      <h1>
        <i class="fa-solid fa-layer-group"></i>
        注采生产监测与预警系统
        <span style="font-size:14px; font-weight:normal; margin-left:10px; opacity:0.8;"></span>
      </h1>
      <div class="header-info">
        <span class="user-name"><i class="fa-regular fa-user-circle" style="font-size:16px;"></i> {{ currentUserName }}</span>

        <button class="logout-btn" @click="handleLogout" title="退出系统">
          <i class="fa-solid fa-right-from-bracket"></i> 退出
        </button>
      </div>
    </header>

    <div class="main-container">
      <div class="sidebar">
        <p class="menu-title">功能导航</p>
        <ul class="menu-list">
          <router-link to="/" class="menu-item" active-class="active">
            <i class="fa-solid fa-map-location-dot"></i> <span>地图总览</span>
          </router-link>

          <router-link to="/list" class="menu-item" active-class="active">
            <i class="fa-solid fa-table-list"></i> <span>油水井台账</span>
          </router-link>

          <router-link to="/dashboard" class="menu-item" active-class="active">
            <i class="fa-solid fa-chart-pie"></i> <span>分析预警大屏</span>
          </router-link>

          <router-link to="/history" class="menu-item" active-class="active">
            <i class="fa-solid fa-clock-rotate-left"></i> <span>历史递减分析</span>
          </router-link>
        </ul>
      </div>

      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

// 🌟 改用 ref，让它变成可变的状态
const currentUserName = ref(localStorage.getItem('userName') || '未登录')

// 🌟 核心修复：监听路由变化。只要路由变了（比如从登录页跳进来了），就重新去读一次缓存里的名字！
watch(() => route.path, () => {
  currentUserName.value = localStorage.getItem('userName') || '未登录'
})

const handleLogout = () => {
  if (confirm('确定要退出注采生产监测与预警系统吗？')) {
    localStorage.removeItem('userRole')
    localStorage.removeItem('userName')
    // 退出时也顺手把名字重置
    currentUserName.value = '未登录'
    router.push('/login')
  }
}
</script>

<style>
/* 全局基础样式 */
:root {
  --primary-color: #2c3e50;
  --accent-color: #3498db;
  --bg-color: #f4f7f6;
  --sidebar-width: 240px;
  --border-color: #e2e8f0;
  --text-main: #2d3748;
  --text-light: #718096;
}

body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; background-color: var(--bg-color); color: var(--text-main); }
.app-container { display: flex; flex-direction: column; height: 100vh; }
header { background: linear-gradient(135deg, var(--primary-color) 0%, #1a252f 100%); color: #ffffff; height: 64px; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; box-shadow: 0 2px 10px rgba(0,0,0,0.15); z-index: 10; }
header h1 { font-size: 20px; margin: 0; font-weight: 600; }
header h1 i { color: var(--accent-color); margin-right: 8px;}

/* 🌟 头部右侧信息区域布局 🌟 */
.header-info {
  display: flex;
  align-items: center;
  gap: 20px;
}
.user-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
}

/* 🌟 退出按钮专属炫酷样式 🌟 */
.logout-btn {
  background: transparent;
  border: 1px solid #e74c3c;
  color: #e74c3c;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s ease;
}
.logout-btn:hover {
  background: #e74c3c;
  color: white;
  box-shadow: 0 2px 8px rgba(231, 76, 60, 0.4);
}

.main-container { display: flex; flex: 1; overflow: hidden; }
.sidebar { width: var(--sidebar-width); background-color: #ffffff; box-shadow: 2px 0 12px rgba(0,0,0,0.03); display: flex; flex-direction: column; padding-top: 20px; z-index: 5; }
.menu-title { font-size: 12px; color: #a0aec0; letter-spacing: 1.5px; margin: 0 0 10px 24px; font-weight: 600; }
.menu-list { list-style: none; padding: 0 16px; margin: 0; display: flex; flex-direction: column; gap: 8px;}

/* 去掉 router-link 的默认下划线 */
a.menu-item { text-decoration: none; }

.menu-item { padding: 14px 16px; border-radius: 10px; color: var(--text-light); display: flex; align-items: center; font-size: 15px; font-weight: 500; cursor: pointer; transition: all 0.3s; }
.menu-item:hover { background-color: #f7fafc; color: var(--primary-color); transform: translateX(4px); }
.menu-item.active { background-color: var(--primary-color); color: #ffffff; box-shadow: 0 4px 12px rgba(44, 62, 80, 0.25); transform: translateX(0);}
.menu-item i { margin-right: 14px; font-size: 18px; width: 20px; text-align: center; }
.content { flex: 1; padding: 24px; overflow-y: auto; }
</style>