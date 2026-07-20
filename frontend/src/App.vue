<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Activity, BarChart3, BookOpen, Bot, ClipboardList, LogOut, Menu, MessagesSquare,
  ScrollText, ShieldCheck, UserRoundCheck, Wifi, WifiOff, X
} from '@lucide/vue'
import { sessionState, hasPermission, logout } from './session'
import { errorMessage, roleLabels } from './ui'

const route = useRoute()
const router = useRouter()
const mobileNav = ref(false)
const online = ref(navigator.onLine)
const logoutError = ref('')

const allNav = [
  { to: '/chat', label: '客户对话', permission: 'chat:send', icon: MessagesSquare },
  { to: '/workspace', label: '坐席工作台', permission: 'conversation:queue', icon: UserRoundCheck },
  { to: '/tickets', label: '工单中心', permission: 'ticket:read', icon: ClipboardList },
  { to: '/knowledge', label: '知识库', permission: 'knowledge:read', icon: BookOpen },
  { to: '/dashboard', label: '运营看板', permission: 'dashboard:read', icon: BarChart3 },
  { to: '/audit', label: '审计日志', permission: 'audit:read', icon: ScrollText }
]

const nav = computed(() => allNav.filter((item) => hasPermission(item.permission)))
const isLogin = computed(() => route.path === '/login')
const healthTone = computed(() => sessionState.health?.status === 'UP' ? 'success' : 'danger')

function setOnline() { online.value = navigator.onLine }
function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') mobileNav.value = false
}

async function signOut() {
  logoutError.value = ''
  try {
    await logout()
  } catch (error) {
    logoutError.value = errorMessage(error)
  } finally {
    await router.replace('/login')
  }
}

watch(() => route.fullPath, () => { mobileNav.value = false })
watch(() => sessionState.user, (user) => {
  if (!user && route.path !== '/login') void router.replace('/login')
})

onMounted(() => {
  window.addEventListener('online', setOnline)
  window.addEventListener('offline', setOnline)
  window.addEventListener('keydown', handleGlobalKeydown)
})
onUnmounted(() => {
  window.removeEventListener('online', setOnline)
  window.removeEventListener('offline', setOnline)
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<template>
  <div v-if="isLogin" class="public-shell"><RouterView /></div>
  <div v-else class="app-shell">
    <header class="topbar">
      <div class="brand-lockup">
        <div class="brand-mark" aria-hidden="true"><Bot :size="22" /></div>
        <div>
          <div class="brand-name">智答服务中枢</div>
          <div class="brand-caption">AI SERVICE OPERATIONS</div>
        </div>
      </div>

      <div class="top-status">
        <span class="source-pill" :class="sessionState.health?.mode === 'DEMO' ? 'is-mock' : 'is-real'">
          <ShieldCheck :size="14" />
          {{ sessionState.health?.mode === 'DEMO' ? 'DEMO · MOCK 数据' : 'STANDARD · 真实 Adapter' }}
        </span>
        <span class="connection-pill" :class="online ? healthTone : 'danger'">
          <Wifi v-if="online" :size="14" /><WifiOff v-else :size="14" />
          {{ online ? (sessionState.health?.status === 'UP' ? '服务在线' : '服务待检') : '网络离线' }}
        </span>
        <button class="icon-button mobile-only" aria-label="打开导航" aria-controls="app-sidebar" :aria-expanded="mobileNav" @click="mobileNav = true"><Menu /></button>
      </div>
    </header>

    <aside id="app-sidebar" class="sidebar" :class="{ open: mobileNav }">
      <div class="mobile-nav-head">
        <span>功能导航</span>
        <button class="icon-button" aria-label="关闭导航" @click="mobileNav = false"><X /></button>
      </div>
      <nav class="side-nav" aria-label="主导航">
        <RouterLink v-for="item in nav" :key="item.to" :to="item.to" class="side-link" active-class="active">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="side-foot">
        <div class="operator-card">
          <span class="operator-avatar">{{ sessionState.user?.displayName.slice(0, 1) }}</span>
          <div class="operator-copy">
            <strong>{{ sessionState.user?.displayName }}</strong>
            <span>{{ roleLabels[sessionState.user?.role || ''] }} · {{ sessionState.user?.username }}</span>
          </div>
        </div>
        <button class="side-link logout-link" @click="signOut"><LogOut :size="18" />退出登录</button>
        <p v-if="logoutError" class="inline-error">{{ logoutError }}</p>
      </div>
    </aside>
    <button v-if="mobileNav" class="nav-scrim" aria-label="关闭导航" @click="mobileNav = false" />

    <main class="page-frame">
      <div class="page-context">
        <div><span class="context-eyebrow">CONTROL DESK</span><h1>{{ route.meta.title }}</h1></div>
        <div class="context-runtime"><Activity :size="15" />{{ sessionState.health?.llm.provider || 'LLM 未知' }}</div>
      </div>
      <RouterView />
    </main>
  </div>
</template>
