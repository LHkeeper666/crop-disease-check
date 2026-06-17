import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './styles/main.css'
import 'highlight.js/styles/github-dark.css'

import LoginView from './views/LoginView.vue'
import PendingView from './views/PendingView.vue'
import MainLayout from './layouts/MainLayout.vue'
import DashboardView from './views/DashboardView.vue'
import DeviceManagementView from './views/DeviceManagementView.vue'
import WorkOrderView from './views/WorkOrderView.vue'
import ReportsView from './views/ReportsView.vue'
import AgentView from './views/AgentView.vue'
import DetectionView from './views/DetectionView.vue'
import CameraMonitorView from './views/CameraMonitorView.vue'
import HandbookView from './views/HandbookView.vue'
import { useAuthStore, roleRouteMap } from './stores/auth'

const routes = [
  { path: '/login', name: 'Login', component: LoginView, meta: { public: true } },
  { path: '/pending', name: 'Pending', component: PendingView, meta: { public: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: DashboardView },
      { path: 'devices', name: 'Devices', component: DeviceManagementView },
      { path: 'workorders', name: 'WorkOrders', component: WorkOrderView },
      { path: 'reports', name: 'Reports', component: ReportsView },
      { path: 'detection', name: 'Detection', component: DetectionView },
      { path: 'monitor', name: 'Monitor', component: CameraMonitorView },
      { path: 'agent', name: 'Agent', component: AgentView },
      { path: 'handbook', name: 'Handbook', component: HandbookView },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  // 公开页面（登录、注册、待审核）不需要认证
  if (to.meta.public) return

  // 每次打开浏览器/新标签页，必须重新登录
  // sessionStorage 仅在当前标签页会话内有效，关闭浏览器或新标签页即清除
  const sessionAuth = sessionStorage.getItem('treeforge_session_auth')
  if (!sessionAuth) {
    localStorage.removeItem('treeforge_token')
    return { name: 'Login' }
  }

  // 会话已验证，检查 token 是否存在
  const token = localStorage.getItem('treeforge_token')
  if (!token) {
    sessionStorage.removeItem('treeforge_session_auth')
    return { name: 'Login' }
  }

  // 待审核用户只能访问 Pending 页面
  const pendingKey = 'treeforge_user_pending'
  const isPending = localStorage.getItem(pendingKey) === 'true'
  if (isPending && to.name !== 'Pending') {
    return { name: 'Pending' }
  }

  // 角色权限校验：检查当前用户角色是否有权访问目标路由
  // 防止用户通过手动输入URL访问未授权页面
  const targetPath = '/' + (to.path === '/' ? 'dashboard' : to.path.replace(/^\//, ''))
  // 找到匹配的子路由路径
  const childPath = to.matched.length > 1
    ? '/' + to.path.split('/').filter(Boolean).pop()
    : targetPath
  const auth = useAuthStore()
  const allowedRoutes = roleRouteMap[auth.userRole] || roleRouteMap.STAFF
  // 只对子路由做权限校验（MainLayout 本身不做限制）
  if (to.matched.length > 1 && !allowedRoutes.includes(childPath)) {
    // 无权限，重定向到首页（首页对所有角色开放）
    return { path: '/dashboard' }
  }
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
