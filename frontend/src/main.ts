import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import ChatView from './views/ChatView.vue'
import TicketsView from './views/TicketsView.vue'
import DashboardView from './views/DashboardView.vue'
import './styles.css'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'chat', component: ChatView, meta: { title: '智能对话' } },
    { path: '/tickets', name: 'tickets', component: TicketsView, meta: { title: '坐席工单' } },
    { path: '/dashboard', name: 'dashboard', component: DashboardView, meta: { title: '数据看板' } }
  ]
})

createApp(App).use(router).mount('#app')
