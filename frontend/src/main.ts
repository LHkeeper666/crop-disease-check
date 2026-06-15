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
  const token = localStorage.getItem('treeforge_token')

  // Public pages (login, pending) don't need auth
  if (to.meta.public) return

  // No token -> redirect to login
  if (!token) {
    return { name: 'Login' }
  }

  // Has token but might be pending - check via a data attribute stored at login time
  const pendingKey = 'treeforge_user_pending'
  const isPending = localStorage.getItem(pendingKey) === 'true'
  if (isPending && to.name !== 'Pending') {
    return { name: 'Pending' }
  }
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
