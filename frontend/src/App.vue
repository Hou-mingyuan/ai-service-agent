<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

const nav = [
  { to: '/chat', label: '智能对话', icon: '💬' },
  { to: '/tickets', label: '坐席工单', icon: '🎫' },
  { to: '/dashboard', label: '数据看板', icon: '📊' }
]
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">
        <div class="logo">智</div>
        <div>
          <div class="brand-name">智答 · AI 智能客服 / 工单 Agent</div>
          <div class="brand-sub">对话式客服 · 工具调用 · 工单闭环</div>
        </div>
      </div>
      <nav class="nav">
        <RouterLink v-for="n in nav" :key="n.to" :to="n.to" class="nav-item" active-class="active">
          <span>{{ n.icon }}</span>{{ n.label }}
        </RouterLink>
      </nav>
    </header>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app { display: flex; flex-direction: column; height: 100%; }
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.brand { display: flex; align-items: center; gap: 12px; }
.logo {
  width: 40px; height: 40px; border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff; font-weight: 700; font-size: 20px;
  display: grid; place-items: center;
}
.brand-name { font-weight: 700; font-size: 16px; }
.brand-sub { font-size: 12px; color: var(--muted); }
.nav { display: flex; gap: 6px; }
.nav-item {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 10px;
  text-decoration: none; color: var(--muted); font-weight: 600;
}
.nav-item:hover { background: var(--primary-soft); color: var(--primary-600); }
.nav-item.active { background: var(--primary); color: #fff; }
.content { flex: 1; overflow: hidden; padding: 20px 24px; }
</style>
