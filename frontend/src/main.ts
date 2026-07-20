import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import { bootstrapSession, hasPermission, homeForRole, sessionState } from './session'
import './styles.css'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    permission?: string
  }
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    {
      path: '/login',
      name: 'login',
      component: () => import('./views/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('./views/ChatView.vue'),
      meta: { title: '客户对话', permission: 'chat:send' }
    },
    {
      path: '/workspace',
      name: 'workspace',
      component: () => import('./views/AgentWorkspaceView.vue'),
      meta: { title: '坐席工作台', permission: 'conversation:queue' }
    },
    {
      path: '/tickets',
      name: 'tickets',
      component: () => import('./views/TicketsView.vue'),
      meta: { title: '工单中心', permission: 'ticket:read' }
    },
    {
      path: '/knowledge',
      name: 'knowledge',
      component: () => import('./views/KnowledgeView.vue'),
      meta: { title: '知识库', permission: 'knowledge:read' }
    },
    {
      path: '/audit',
      name: 'audit',
      component: () => import('./views/AuditView.vue'),
      meta: { title: '审计日志', permission: 'audit:read' }
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('./views/DashboardView.vue'),
      meta: { title: '运营看板', permission: 'dashboard:read' }
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('./views/ForbiddenView.vue'),
      meta: { title: '无访问权限' }
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  await bootstrapSession()
  if (to.path === '/') return homeForRole(sessionState.user?.role)
  if (!sessionState.user && to.path !== '/login') return '/login'
  if (sessionState.user && to.path === '/login') return homeForRole(sessionState.user.role)
  if (to.meta.permission && !hasPermission(to.meta.permission)) return '/forbidden'
  document.title = `${to.meta.title || '工作台'} · 智答客服`
  return true
})

createApp(App).use(router).mount('#app')
