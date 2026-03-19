import { createRouter, createWebHistory } from 'vue-router'
import MapView from '../views/MapView.vue'
import WellListView from '../views/WellListView.vue'
import DashboardView from '../views/DashboardView.vue'
import HistoryView from '../views/HistoryView.vue'
import LoginView from '../views/LoginView.vue' // 🌟 引入登录页

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        { path: '/login', name: 'login', component: LoginView }, // 🌟 登录路由
        { path: '/', name: 'map', component: MapView },
        { path: '/list', name: 'list', component: WellListView },
        { path: '/dashboard', name: 'dashboard', component: DashboardView },
        { path: '/history', name: 'history', component: HistoryView }
    ]
})

// 🌟 核心：使用 Vue Router 4 的最新 return 语法，彻底消灭黄字警告 🌟
router.beforeEach((to, from) => {
    // 获取本地存储中的角色信息
    const role = localStorage.getItem('userRole')

    // 如果去的不是登录页，且没有登录信息，强制打回登录页！
    if (to.name !== 'login' && !role) {
        return { name: 'login' } // 以前是 next({ name: 'login' })，现在直接 return
    }
    // 如果已经登录了，还想去登录页，直接跳转到首页
    else if (to.name === 'login' && role) {
        return { name: 'map' }
    }
    // 其他情况正常放行
    return true
})

export default router