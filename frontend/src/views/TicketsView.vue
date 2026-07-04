<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, connectAgentSocket } from '../api'

const tickets = ref<any[]>([])
const detail = ref<{ ticket: any; events: any[] } | null>(null)
const filterStatus = ref('')
const assignee = ref('')
const toast = ref('')
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
  OPEN: '待处理', IN_PROGRESS: '处理中', PENDING: '挂起', RESOLVED: '已解决', CLOSED: '已关闭'
}
const statusClass: Record<string, string> = {
  OPEN: 'badge-amber', IN_PROGRESS: 'badge-blue', PENDING: 'badge-gray',
  RESOLVED: 'badge-green', CLOSED: 'badge-gray'
}
const prioLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }
const prioClass: Record<string, string> = {
  LOW: 'badge-gray', MEDIUM: 'badge-blue', HIGH: 'badge-amber', URGENT: 'badge-red'
}
const catLabel: Record<string, string> = {
  ORDER: '订单', LOGISTICS: '物流', POLICY: '保单', REFUND: '退款', COMPLAINT: '投诉', OTHER: '其它'
}

const nextStates = computed(() =>
  detail.value ? allowedNext[detail.value.ticket.status] || [] : []
)

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
  const q = filterStatus.value ? `?status=${filterStatus.value}` : ''
  tickets.value = await api.get<any[]>(`/api/tickets${q}`)
}

async function openDetail(id: number) {
  detail.value = await api.get(`/api/tickets/${id}`)
  assignee.value = detail.value?.ticket.assignee || ''
}

async function doTransition(toStatus: string) {
  if (!detail.value) return
  await api.post(`/api/tickets/${detail.value.ticket.id}/transition`, {
    toStatus, operator: '坐席-你', note: `坐席操作：置为${statusLabel[toStatus]}`
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

let toastTimer: any
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
  <div class="tickets-layout">
    <section class="list card">
      <div class="list-head">
        <b>工单列表</b>
        <select v-model="filterStatus" @change="loadTickets">
          <option v-for="s in statusOptions" :key="s" :value="s">
            {{ s === '' ? '全部状态' : statusLabel[s] }}
          </option>
        </select>
      </div>
      <div class="list-body">
        <div
          v-for="t in tickets"
          :key="t.id"
          class="ticket-item"
          :class="{ active: detail?.ticket.id === t.id }"
          @click="openDetail(t.id)"
        >
          <div class="ti-top">
            <b>{{ t.ticketNo }}</b>
            <span class="badge" :class="prioClass[t.priority]">{{ prioLabel[t.priority] }}</span>
          </div>
          <div class="ti-title">{{ t.title }}</div>
          <div class="ti-foot">
            <span class="badge" :class="statusClass[t.status]">{{ statusLabel[t.status] }}</span>
            <span class="badge badge-gray">{{ catLabel[t.category] || t.category }}</span>
            <span class="ti-cust">{{ t.customer || '—' }}</span>
          </div>
        </div>
        <div v-if="tickets.length === 0" class="empty">暂无工单</div>
      </div>
    </section>

    <section class="detail card" v-if="detail">
      <div class="detail-head">
        <div>
          <div class="dh-no">{{ detail.ticket.ticketNo }}</div>
          <div class="dh-title">{{ detail.ticket.title }}</div>
        </div>
        <span class="badge" :class="statusClass[detail.ticket.status]">
          {{ statusLabel[detail.ticket.status] }}
        </span>
      </div>

      <div class="grid">
        <div><span>类别</span><b>{{ catLabel[detail.ticket.category] || detail.ticket.category }}</b></div>
        <div><span>优先级</span><b>{{ prioLabel[detail.ticket.priority] }}</b></div>
        <div><span>来源</span><b>{{ detail.ticket.source === 'AGENT' ? '机器人自动' : '人工' }}</b></div>
        <div><span>客户</span><b>{{ detail.ticket.customer || '—' }}</b></div>
        <div><span>处理人</span><b>{{ detail.ticket.assignee || '待分配' }}</b></div>
        <div><span>创建时间</span><b>{{ fmt(detail.ticket.createdAt) }}</b></div>
      </div>

      <div class="desc">{{ detail.ticket.description }}</div>

      <div class="actions">
        <div class="assign">
          <input v-model="assignee" placeholder="指派处理人，如 Agent-01" />
          <button class="btn-ghost" @click="doAssign">指派 / 受理</button>
        </div>
        <div class="transitions">
          <button
            v-for="s in nextStates"
            :key="s"
            class="btn-outline"
            @click="doTransition(s)"
          >
            → {{ statusLabel[s] }}
          </button>
          <span v-if="nextStates.length === 0" class="closed-tip">该工单已关闭（终态）</span>
        </div>
      </div>

      <div class="timeline-title">流转记录</div>
      <div class="timeline">
        <div v-for="e in detail.events" :key="e.id" class="tl-item">
          <div class="tl-dot"></div>
          <div class="tl-body">
            <div class="tl-line">
              <b>{{ e.fromStatus ? statusLabel[e.fromStatus] : '创建' }} → {{ statusLabel[e.toStatus] }}</b>
              <span class="tl-op">{{ e.operator }}</span>
            </div>
            <div class="tl-note">{{ e.note }}</div>
            <div class="tl-time">{{ fmt(e.createdAt) }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="detail card empty-detail" v-else>
      <div class="empty">← 请选择左侧工单查看详情并处理</div>
    </section>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<style scoped>
.tickets-layout { display: grid; grid-template-columns: 360px 1fr; gap: 16px; height: 100%; position: relative; }
.list { display: flex; flex-direction: column; overflow: hidden; }
.list-head { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid var(--border); }
.list-body { overflow-y: auto; padding: 10px; }
.ticket-item { padding: 12px; border-radius: 12px; cursor: pointer; border: 1px solid transparent; margin-bottom: 8px; background: #f8fafc; }
.ticket-item:hover { background: #f1f5f9; }
.ticket-item.active { border-color: var(--primary); background: var(--primary-soft); }
.ti-top { display: flex; justify-content: space-between; align-items: center; }
.ti-title { font-size: 13px; margin: 4px 0; color: #334155; }
.ti-foot { display: flex; align-items: center; gap: 6px; }
.ti-cust { color: var(--muted); font-size: 12px; margin-left: auto; }

.detail { padding: 20px; overflow-y: auto; }
.detail-head { display: flex; justify-content: space-between; align-items: flex-start; }
.dh-no { color: var(--muted); font-size: 13px; }
.dh-title { font-size: 18px; font-weight: 700; margin-top: 2px; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 18px 0; }
.grid div { display: flex; flex-direction: column; gap: 2px; }
.grid span { color: var(--muted); font-size: 12px; }
.desc { background: #f8fafc; border-radius: 12px; padding: 12px 14px; color: #334155; }

.actions { margin: 18px 0; display: flex; flex-direction: column; gap: 12px; }
.assign { display: flex; gap: 8px; }
.assign input { flex: 1; }
.transitions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.closed-tip { color: var(--muted); font-size: 13px; }

.timeline-title { font-weight: 700; margin: 8px 0 12px; }
.timeline { display: flex; flex-direction: column; }
.tl-item { display: flex; gap: 12px; padding-bottom: 16px; position: relative; }
.tl-item:not(:last-child)::before { content: ''; position: absolute; left: 5px; top: 14px; bottom: 0; width: 2px; background: var(--border); }
.tl-dot { width: 12px; height: 12px; border-radius: 50%; background: var(--primary); margin-top: 3px; flex-shrink: 0; z-index: 1; }
.tl-line { display: flex; gap: 10px; align-items: center; }
.tl-op { color: var(--muted); font-size: 12px; }
.tl-note { color: #475569; font-size: 13px; margin: 2px 0; }
.tl-time { color: var(--muted); font-size: 12px; }

.empty { color: var(--muted); text-align: center; padding: 40px 0; }
.empty-detail { display: grid; place-items: center; }

.toast { position: absolute; top: 12px; left: 50%; transform: translateX(-50%); background: #0f172a; color: #fff; padding: 10px 18px; border-radius: 999px; font-size: 13px; box-shadow: var(--shadow); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 900px) {
  .tickets-layout { grid-template-columns: 1fr; }
}
</style>
