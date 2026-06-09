import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './styles/main.css'

import LoginView from './views/LoginView.vue'
import MainLayout from './layouts/MainLayout.vue'
import DashboardView from './views/DashboardView.vue'
import DeviceManagementView from './views/DeviceManagementView.vue'
import WorkOrderView from './views/WorkOrderView.vue'
import ReportsView from './views/ReportsView.vue'
import AgentView from './views/AgentView.vue'

const routes = [
  { path: '/login', name: 'Login', component: LoginView, meta: { public: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: DashboardView },
      { path: 'devices', name: 'Devices', component: DeviceManagementView },
      { path: 'workorders', name: 'WorkOrders', component: WorkOrderView },
      { path: 'reports', name: 'Reports', component: ReportsView },
      { path: 'agent', name: 'Agent', component: AgentView },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('treeforge_token')
  if (!to.meta.public && !token) {
    return { name: 'Login' }
  }
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
