<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, connectAgentSocket } from '../api'

const tickets = ref<any[]>([])
const detail = ref<{ ticket: any; events: any[] } | null>(null)
const filterStatus = ref('')
const search = ref('')
const assignee = ref('')
const toast = ref('')
const loading = ref(false)
let ws: WebSocket | null = null

const statusOptions = ['', 'OPEN', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED']
const allowedNext: Record<string, string[]> = {
  OPEN: ['IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED'],
  IN_PROGRESS: ['PENDING', 'RESOLVED', 'CLOSED'],
  PENDING: ['IN_PROGRESS', 'RESOLVED', 'CLOSED'],
  RESOLVED: ['IN_PROGRESS', 'CLOSED'],
  CLOSED: []
}
const statusLabel: Record<string, string> = {
  OPEN: '待处理',
  IN_PROGRESS: '处理中',
  PENDING: '挂起',
  RESOLVED: '已解决',
  CLOSED: '已关闭'
}
const statusClass: Record<string, string> = {
  OPEN: 'badge-amber',
  IN_PROGRESS: 'badge-blue',
  PENDING: 'badge-gray',
  RESOLVED: 'badge-green',
  CLOSED: 'badge-gray'
}
const prioLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }
const prioClass: Record<string, string> = {
  LOW: 'badge-gray',
  MEDIUM: 'badge-blue',
  HIGH: 'badge-amber',
  URGENT: 'badge-red'
}
const catLabel: Record<string, string> = {
  ORDER: '订单',
  LOGISTICS: '物流',
  POLICY: '保单',
  REFUND: '退款',
  COMPLAINT: '投诉',
  OTHER: '其它'
}

const nextStates = computed(() =>
  detail.value ? allowedNext[detail.value.ticket.status] || [] : []
)

const filteredTickets = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return tickets.value
  return tickets.value.filter(
    (t) =>
      t.ticketNo?.toLowerCase().includes(q) ||
      t.title?.toLowerCase().includes(q) ||
      t.customer?.toLowerCase().includes(q)
  )
})

const stats = computed(() => {
  const all = tickets.value
  return {
    total: all.length,
    open: all.filter((t) => t.status === 'OPEN' || t.status === 'IN_PROGRESS').length,
    urgent: all.filter((t) => t.priority === 'URGENT' || t.priority === 'HIGH').length
  }
})

onMounted(() => {
  loadTickets()
  ws = connectAgentSocket((type, payload) => {
    showToast(
      type === 'ticket.created'
        ? `🆕 新工单 ${payload.ticketNo}`
        : `🔄 工单 ${payload.ticketNo} → ${statusLabel[payload.status] || payload.status}`
    )
    loadTickets()
    if (detail.value && payload.id === detail.value.ticket.id) openDetail(payload.id)
  })
})
onBeforeUnmount(() => ws?.close())

async function loadTickets() {
  loading.value = true
  try {
    const q = filterStatus.value ? `?status=${filterStatus.value}` : ''
    tickets.value = await api.get<any[]>(`/api/tickets${q}`)
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  detail.value = await api.get(`/api/tickets/${id}`)
  assignee.value = detail.value?.ticket.assignee || ''
}

async function doTransition(toStatus: string) {
  if (!detail.value) return
  await api.post(`/api/tickets/${detail.value.ticket.id}/transition`, {
    toStatus,
    operator: '坐席-你',
    note: `坐席操作：置为${statusLabel[toStatus]}`
  })
  await openDetail(detail.value.ticket.id)
  await loadTickets()
}

async function doAssign() {
  if (!detail.value || !assignee.value.trim()) return
  await api.post(`/api/tickets/${detail.value.ticket.id}/assign`, { assignee: assignee.value.trim() })
  await openDetail(detail.value.ticket.id)
  await loadTickets()
}

let toastTimer: ReturnType<typeof setTimeout>
function showToast(msg: string) {
  toast.value = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2600)
}

function fmt(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<template>
  <div class="tickets-page">
    <section class="list card">
      <header class="list-head">
        <div>
          <b>坐席工单</b>
          <div class="list-sub">WebSocket 实时推送 · 状态机流转</div>
        </div>
        <button class="btn-outline btn-sm" :disabled="loading" @click="loadTickets">
          {{ loading ? '…' : '↻' }}
        </button>
      </header>

      <div class="stats-row">
        <div class="stat"><span>{{ stats.total }}</span><label>全部</label></div>
        <div class="stat warn"><span>{{ stats.open }}</span><label>待办</label></div>
        <div class="stat danger"><span>{{ stats.urgent }}</span><label>高优</label></div>
      </div>

      <div class="filters">
        <input v-model="search" placeholder="搜索工单号 / 标题 / 客户…" />
        <select v-model="filterStatus" @change="loadTickets">
          <option v-for="s in statusOptions" :key="s" :value="s">
            {{ s === '' ? '全部状态' : statusLabel[s] }}
          </option>
        </select>
      </div>

      <div class="list-body">
        <div
          v-for="t in filteredTickets"
          :key="t.id"
          class="ticket-card"
          :class="{ active: detail?.ticket.id === t.id, urgent: t.priority === 'URGENT' }"
          @click="openDetail(t.id)"
        >
          <div class="tc-top">
            <span class="tc-no">{{ t.ticketNo }}</span>
            <span class="badge" :class="prioClass[t.priority]">{{ prioLabel[t.priority] }}</span>
          </div>
          <div class="tc-title">{{ t.title }}</div>
          <div class="tc-foot">
            <span class="badge" :class="statusClass[t.status]">{{ statusLabel[t.status] }}</span>
            <span class="badge badge-gray">{{ catLabel[t.category] || t.category }}</span>
            <span class="tc-cust">{{ t.customer || '—' }}</span>
          </div>
        </div>
        <div v-if="filteredTickets.length === 0" class="empty">
          {{ tickets.length === 0 ? '暂无工单，可在对话页发送投诉话术自动建单' : '无匹配结果' }}
        </div>
      </div>
    </section>

    <section class="detail card" v-if="detail">
      <header class="detail-head">
        <div>
          <div class="dh-badges">
            <span class="badge" :class="statusClass[detail.ticket.status]">
              {{ statusLabel[detail.ticket.status] }}
            </span>
            <span class="badge" :class="prioClass[detail.ticket.priority]">
              {{ prioLabel[detail.ticket.priority] }}
            </span>
            <span class="badge badge-gray">{{ catLabel[detail.ticket.category] }}</span>
          </div>
          <div class="dh-no">{{ detail.ticket.ticketNo }}</div>
          <h2 class="dh-title">{{ detail.ticket.title }}</h2>
        </div>
      </header>

      <div class="info-grid">
        <div><label>来源</label><b>{{ detail.ticket.source === 'AGENT' ? '机器人自动' : '人工' }}</b></div>
        <div><label>客户</label><b>{{ detail.ticket.customer || '—' }}</b></div>
        <div><label>处理人</label><b>{{ detail.ticket.assignee || '待分配' }}</b></div>
        <div><label>创建</label><b>{{ fmt(detail.ticket.createdAt) }}</b></div>
      </div>

      <div class="desc-box">
        <label>问题描述</label>
        <p>{{ detail.ticket.description }}</p>
      </div>

      <div class="action-panel">
        <div class="assign-row">
          <input v-model="assignee" placeholder="指派处理人，如 Agent-01" />
          <button class="btn-primary" @click="doAssign">指派 / 受理</button>
        </div>
        <div class="transition-row">
          <span class="trans-label">状态流转</span>
          <button v-for="s in nextStates" :key="s" class="btn-outline" @click="doTransition(s)">
            → {{ statusLabel[s] }}
          </button>
          <span v-if="nextStates.length === 0" class="closed-tip">终态，不可再流转</span>
        </div>
      </div>

      <div class="timeline-block">
        <div class="tl-title">流转时间线</div>
        <div class="timeline">
          <div v-for="e in detail.events" :key="e.id" class="tl-item">
            <div class="tl-rail"><span class="tl-dot"></span></div>
            <div class="tl-content">
              <div class="tl-head">
                <b>{{ e.fromStatus ? statusLabel[e.fromStatus] : '创建' }} → {{ statusLabel[e.toStatus] }}</b>
                <span>{{ e.operator }}</span>
              </div>
              <p>{{ e.note }}</p>
              <time>{{ fmt(e.createdAt) }}</time>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="detail card placeholder" v-else>
      <div class="placeholder-inner">
        <div class="ph-icon">🎫</div>
        <b>选择左侧工单</b>
        <p>查看详情、指派处理人并完成 OPEN → IN_PROGRESS → RESOLVED 闭环</p>
      </div>
    </section>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<style scoped>
.tickets-page {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  height: 100%;
  min-height: 0;
  position: relative;
}
.list { display: flex; flex-direction: column; overflow: hidden; min-height: 0; }
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.list-sub { font-size: 12px; color: var(--muted); margin-top: 2px; }
.btn-sm { padding: 6px 10px; font-size: 13px; }

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
}
.stat {
  text-align: center;
  padding: 8px;
  border-radius: 10px;
  background: #f8fafc;
}
.stat span { display: block; font-size: 20px; font-weight: 700; }
.stat label { font-size: 11px; color: var(--muted); }
.stat.warn span { color: #d97706; }
.stat.danger span { color: #dc2626; }

.filters {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
}
.filters input { flex: 1; min-width: 0; }
.filters select { width: 110px; flex-shrink: 0; }

.list-body { flex: 1; overflow-y: auto; padding: 10px 12px; }

.ticket-card {
  padding: 12px 14px;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 8px;
  background: #f8fafc;
  transition: border-color 0.15s, background 0.15s;
}
.ticket-card:hover { background: #f1f5f9; }
.ticket-card.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: 0 0 0 1px rgba(79, 70, 229, 0.15);
}
.ticket-card.urgent:not(.active) { border-color: #fecaca; }
.tc-top { display: flex; justify-content: space-between; align-items: center; }
.tc-no { font-weight: 700; font-size: 13px; font-family: ui-monospace, monospace; }
.tc-title { font-size: 13px; margin: 6px 0; color: #334155; line-height: 1.4; }
.tc-foot { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.tc-cust { color: var(--muted); font-size: 12px; margin-left: auto; }

.detail { padding: 0; overflow-y: auto; min-height: 0; display: flex; flex-direction: column; }
.detail-head { padding: 20px 22px 16px; border-bottom: 1px solid var(--border); }
.dh-badges { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
.dh-no { color: var(--muted); font-size: 13px; font-family: ui-monospace, monospace; }
.dh-title { margin: 4px 0 0; font-size: 20px; font-weight: 700; }

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--border);
}
.info-grid label { display: block; font-size: 11px; color: var(--muted); margin-bottom: 2px; }
.info-grid b { font-size: 14px; }

.desc-box {
  padding: 16px 22px;
  border-bottom: 1px solid var(--border);
}
.desc-box label { font-size: 12px; color: var(--muted); font-weight: 600; }
.desc-box p {
  margin: 8px 0 0;
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px 14px;
  color: #334155;
  line-height: 1.55;
}

.action-panel { padding: 16px 22px; border-bottom: 1px solid var(--border); }
.assign-row { display: flex; gap: 8px; margin-bottom: 12px; }
.assign-row input { flex: 1; }
.transition-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.trans-label { font-size: 12px; color: var(--muted); margin-right: 4px; }
.closed-tip { color: var(--muted); font-size: 13px; }

.timeline-block { padding: 16px 22px 22px; flex: 1; }
.tl-title { font-weight: 700; margin-bottom: 14px; }
.timeline { display: flex; flex-direction: column; gap: 0; }
.tl-item { display: flex; gap: 12px; }
.tl-rail {
  width: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 4px;
}
.tl-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--primary);
  flex-shrink: 0;
}
.tl-item:not(:last-child) .tl-rail::after {
  content: '';
  flex: 1;
  width: 2px;
  background: var(--border);
  margin-top: 4px;
}
.tl-content { padding-bottom: 18px; flex: 1; }
.tl-head { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
.tl-head span { color: var(--muted); font-size: 12px; }
.tl-content p { margin: 4px 0; color: #475569; font-size: 13px; }
.tl-content time { color: var(--muted); font-size: 12px; }

.placeholder { display: grid; place-items: center; }
.placeholder-inner { text-align: center; padding: 40px; max-width: 320px; }
.ph-icon { font-size: 48px; margin-bottom: 12px; opacity: 0.6; }
.placeholder-inner p { color: var(--muted); font-size: 13px; margin-top: 8px; }

.empty { color: var(--muted); text-align: center; padding: 32px 16px; font-size: 13px; line-height: 1.5; }

.toast {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: #0f172a;
  color: #fff;
  padding: 10px 18px;
  border-radius: 999px;
  font-size: 13px;
  box-shadow: var(--shadow);
  z-index: 10;
}
.fade-enter-active,
.fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

@media (max-width: 900px) {
  .tickets-page { grid-template-columns: 1fr; }
  .info-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
