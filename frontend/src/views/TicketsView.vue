<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  AlertTriangle, CalendarClock, CheckCircle2, ChevronLeft, ChevronRight, CircleDot,
  ClipboardCheck, ClipboardList, History, RefreshCw, RotateCcw, Search, UserRoundPlus
} from '@lucide/vue'
import { api, RealtimeClient } from '../api'
import { hasPermission, sessionState } from '../session'
import type { PageResult, RealtimeEnvelope, Ticket, TicketDetails, TicketStatus } from '../types'
import { categoryLabels, errorMessage, formatDate, priorityLabels, statusLabels, statusTone } from '../ui'

const tickets = ref<Ticket[]>([])
const total = ref(0)
const pages = ref(0)
const page = ref(1)
const size = 30
const status = ref('')
const category = ref('')
const search = ref('')
const detail = ref<TicketDetails | null>(null)
const loading = ref(true)
const detailLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const notice = ref('')
const now = ref(Date.now())
const action = reactive({ targetStatus: '' as TicketStatus | '', note: '', closeReason: '', assignee: '', reopenReason: '' })
let realtime: RealtimeClient | null = null
let clock: ReturnType<typeof setInterval> | null = null

const allowedNext: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ['IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED'],
  IN_PROGRESS: ['PENDING', 'RESOLVED', 'CLOSED'],
  PENDING: ['IN_PROGRESS', 'RESOLVED', 'CLOSED'],
  RESOLVED: ['CLOSED'], CLOSED: []
}
const statusOptions: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED']
const categories = ['ORDER', 'LOGISTICS', 'POLICY', 'REFUND', 'COMPLAINT', 'OTHER']
const visibleTickets = computed(() => {
  const query = search.value.trim().toLowerCase()
  if (!query) return tickets.value
  return tickets.value.filter((item) => [item.ticketNo, item.title, item.customer, item.assignee]
    .filter(Boolean).some((value) => String(value).toLowerCase().includes(query)))
})
const nextStatuses = computed(() => detail.value ? allowedNext[detail.value.ticket.status] : [])
const canAssign = computed(() => hasPermission('ticket:assign'))
const canMutateSelected = computed(() => {
  const ticket = detail.value?.ticket
  return !!ticket && (canAssign.value || !ticket.assignee || ticket.assignee === sessionState.user?.username)
})
const sla = computed(() => {
  const ticket = detail.value?.ticket
  if (!ticket?.slaDueAt || ['RESOLVED', 'CLOSED'].includes(ticket.status)) return { label: 'SLA 已停止', tone: 'neutral', percent: 100 }
  const due = new Date(ticket.slaDueAt).getTime()
  const created = new Date(ticket.createdAt).getTime()
  const remaining = due - now.value
  const totalWindow = Math.max(1, due - created)
  const percent = Math.min(100, Math.max(0, ((now.value - created) / totalWindow) * 100))
  if (ticket.slaBreachedAt || remaining <= 0) return { label: 'SLA 已超时', tone: 'danger', percent: 100 }
  const minutes = Math.ceil(remaining / 60000)
  return { label: `剩余 ${minutes} 分钟`, tone: minutes <= 5 ? 'warning' : 'success', percent }
})

onMounted(async () => {
  await loadTickets()
  const username = sessionState.user?.username
  if (username) {
    realtime = new RealtimeClient(username, handleRealtime, () => undefined)
    realtime.connect()
  }
  clock = setInterval(() => { now.value = Date.now() }, 30_000)
})
onBeforeUnmount(() => { realtime?.close(); if (clock) clearInterval(clock) })

async function loadTickets(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), size: String(size) })
    if (status.value) params.set('status', status.value)
    if (category.value) params.set('category', category.value)
    const result = await api.get<PageResult<Ticket>>(`/api/tickets?${params}`, { dedupe: false })
    tickets.value = result.records
    total.value = result.total
    pages.value = result.pages
    if (detail.value) {
      const row = result.records.find((item) => item.id === detail.value?.ticket.id)
      if (row) detail.value.ticket = row
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  detailLoading.value = true
  error.value = ''
  try {
    detail.value = await api.get<TicketDetails>(`/api/tickets/${id}`, { dedupe: false })
    resetAction()
    action.assignee = detail.value.ticket.assignee || ''
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    detailLoading.value = false
  }
}

function resetAction() {
  action.targetStatus = ''
  action.note = ''
  action.closeReason = ''
  action.reopenReason = ''
}

async function claim() {
  if (!detail.value) return
  await runAction(async () => {
    const ticket = await api.post<Ticket>(`/api/tickets/${detail.value!.ticket.id}/claim`)
    notice.value = `工单已认领给 ${ticket.assignee}`
  })
}

async function assign() {
  if (!detail.value || !action.assignee.trim()) return
  await runAction(async () => {
    await api.post<Ticket>(`/api/tickets/${detail.value!.ticket.id}/assign`, { assignee: action.assignee.trim() })
    notice.value = `工单已指派给 ${action.assignee.trim()}`
  })
}

async function transition() {
  if (!detail.value || !action.targetStatus) return
  if (action.targetStatus === 'RESOLVED' && !action.note.trim()) { error.value = '解决工单必须填写处理结论'; return }
  if (action.targetStatus === 'CLOSED' && !action.closeReason.trim()) { error.value = '关闭工单必须填写关闭原因'; return }
  await runAction(async () => {
    await api.post<Ticket>(`/api/tickets/${detail.value!.ticket.id}/transition`, {
      toStatus: action.targetStatus, note: action.note.trim() || null, closeReason: action.closeReason.trim() || null
    })
    notice.value = `状态已流转为“${statusLabels[action.targetStatus]}”`
    resetAction()
  })
}

async function reopen() {
  if (!detail.value || !action.reopenReason.trim()) { error.value = '重开必须填写原因'; return }
  await runAction(async () => {
    await api.post<Ticket>(`/api/tickets/${detail.value!.ticket.id}/reopen`, { reason: action.reopenReason.trim() })
    notice.value = '工单已重开并重新计算 SLA。'
    resetAction()
  })
}

async function runAction(operation: () => Promise<void>) {
  if (!detail.value || actionLoading.value) return
  actionLoading.value = true
  error.value = ''
  const id = detail.value.ticket.id
  try {
    await operation()
    await Promise.all([openDetail(id), loadTickets()])
  } catch (reason) {
    error.value = errorMessage(reason)
    await openDetail(id).catch(() => undefined)
  } finally {
    actionLoading.value = false
  }
}

function handleRealtime(event: RealtimeEnvelope) {
  if (!event.type.startsWith('ticket.')) return
  const payload = event.payload as Record<string, unknown>
  const id = Number(payload.id || 0)
  if (detail.value?.ticket.id === id) void openDetail(id)
  void loadTickets()
}

async function changePage(next: number) {
  if (next < 1 || next > pages.value) return
  page.value = next
  await loadTickets()
}
</script>

<template>
  <div class="ticket-layout">
    <section class="ticket-list-panel panel">
      <header class="panel-header"><div><span class="panel-index">TICKETS</span><h2>工单队列</h2><p>共 {{ total }} 条</p></div><button class="icon-button" aria-label="刷新工单" :disabled="loading" @click="loadTickets()"><RefreshCw /></button></header>
      <div class="ticket-filters"><div class="search-field"><Search :size="15" /><input v-model="search" placeholder="搜索工单号、客户、标题" /></div><select v-model="status" aria-label="状态筛选" @change="loadTickets(true)"><option value="">全部状态</option><option v-for="value in statusOptions" :key="value" :value="value">{{ statusLabels[value] }}</option></select><select v-model="category" aria-label="类别筛选" @change="loadTickets(true)"><option value="">全部类别</option><option v-for="value in categories" :key="value" :value="value">{{ categoryLabels[value] }}</option></select></div>
      <div v-if="loading" class="loading-line" />
      <div v-if="error" class="ticket-alert"><AlertTriangle :size="15" />{{ error }}<button @click="error = ''">关闭</button></div>
      <div class="ticket-list">
        <button v-for="ticket in visibleTickets" :key="ticket.id" class="ticket-row" :class="{ selected: detail?.ticket.id === ticket.id, urgent: ticket.priority === 'URGENT' }" @click="openDetail(ticket.id)">
          <div><b class="mono">{{ ticket.ticketNo }}</b><span class="badge" :class="statusTone(ticket.status)">{{ statusLabels[ticket.status] }}</span></div><h3>{{ ticket.title }}</h3><p><span>{{ categoryLabels[ticket.category] || ticket.category }}</span><span :class="{ urgent: ticket.priority === 'URGENT' }">{{ priorityLabels[ticket.priority] }}</span><span>{{ ticket.assignee || '未分配' }}</span></p><footer><span>{{ ticket.customer || '未知客户' }}</span><time>{{ formatDate(ticket.updatedAt) }}</time></footer>
        </button>
        <div v-if="!loading && !visibleTickets.length" class="empty-state">{{ tickets.length ? '没有匹配当前搜索条件的工单' : '当前筛选下没有工单' }}</div>
      </div>
      <footer class="pagination"><button class="icon-button" :disabled="page <= 1" @click="changePage(page - 1)"><ChevronLeft /></button><span class="mono">{{ page }} / {{ Math.max(1, pages) }}</span><button class="icon-button" :disabled="page >= pages" @click="changePage(page + 1)"><ChevronRight /></button></footer>
    </section>

    <section class="ticket-detail-panel panel">
      <template v-if="detail">
        <div v-if="detailLoading" class="loading-line" />
        <header class="ticket-detail-head"><div><div class="detail-badges"><span class="badge" :class="statusTone(detail.ticket.status)">{{ statusLabels[detail.ticket.status] }}</span><span class="badge" :class="detail.ticket.priority === 'URGENT' ? 'danger' : detail.ticket.priority === 'HIGH' ? 'warning' : 'neutral'">{{ priorityLabels[detail.ticket.priority] }}</span><span class="badge neutral">{{ categoryLabels[detail.ticket.category] }}</span></div><b class="mono ticket-number">{{ detail.ticket.ticketNo }}</b><h2>{{ detail.ticket.title }}</h2></div><div class="sla-card" :class="sla.tone"><CalendarClock :size="19" /><span><small>SLA DEADLINE</small><b>{{ sla.label }}</b><em>{{ formatDate(detail.ticket.slaDueAt) }}</em></span></div></header>
        <div class="sla-track"><span :class="sla.tone" :style="{ width: `${sla.percent}%` }" /></div>
        <div v-if="notice" class="detail-notice"><CheckCircle2 :size="15" />{{ notice }}<button @click="notice = ''">关闭</button></div>
        <div class="ticket-detail-scroll">
          <section class="ticket-summary"><dl><div><dt>客户</dt><dd>{{ detail.ticket.customer || '—' }}</dd></div><div><dt>处理人</dt><dd>{{ detail.ticket.assignee || '未分配' }}</dd></div><div><dt>来源</dt><dd>{{ detail.ticket.source }}</dd></div><div><dt>创建时间</dt><dd>{{ formatDate(detail.ticket.createdAt) }}</dd></div><div><dt>会话</dt><dd>{{ detail.ticket.conversationId ? `#${detail.ticket.conversationId}` : '无关联' }}</dd></div><div><dt>版本</dt><dd class="mono">v{{ detail.ticket.version }}</dd></div></dl><div class="description"><span class="section-label">DESCRIPTION</span><p>{{ detail.ticket.description || '未填写问题描述' }}</p></div><div v-if="detail.ticket.resolutionNote" class="resolution"><span class="section-label">RESOLUTION</span><p>{{ detail.ticket.resolutionNote }}</p></div><div v-if="detail.ticket.closeReason" class="resolution"><span class="section-label">CLOSE REASON</span><p>{{ detail.ticket.closeReason }}</p></div></section>

          <section class="ticket-actions"><span class="section-label">CONTROL ACTIONS</span><div v-if="!canMutateSelected" class="notice warning"><AlertTriangle :size="15" />此工单已由其他坐席处理，你只能查看；主管可重新指派。</div><div class="action-grid"><div class="action-box"><div><UserRoundPlus :size="16" /><b>{{ canAssign ? '指派处理人' : '认领工单' }}</b></div><template v-if="canAssign"><input v-model="action.assignee" maxlength="64" placeholder="输入坐席账号" /><button class="button" :disabled="actionLoading || !action.assignee.trim()" @click="assign">确认指派</button></template><button v-else class="button primary" :disabled="actionLoading || !!detail.ticket.assignee" @click="claim">{{ detail.ticket.assignee ? `已由 ${detail.ticket.assignee} 处理` : '认领此工单' }}</button></div>
            <div v-if="nextStatuses.length && canMutateSelected" class="action-box transition-box"><div><ClipboardCheck :size="16" /><b>状态流转</b></div><select v-model="action.targetStatus"><option value="">选择目标状态</option><option v-for="value in nextStatuses" :key="value" :value="value">{{ statusLabels[value] }}</option></select><textarea v-model="action.note" rows="2" maxlength="1000" :placeholder="action.targetStatus === 'RESOLVED' ? '处理结论（必填）' : '流转说明（可选）'" /><textarea v-if="action.targetStatus === 'CLOSED'" v-model="action.closeReason" rows="2" maxlength="1000" placeholder="关闭原因（必填）" /><button class="button primary" :disabled="actionLoading || !action.targetStatus" @click="transition">执行流转</button></div>
            <div v-if="['RESOLVED','CLOSED'].includes(detail.ticket.status) && canMutateSelected" class="action-box"><div><RotateCcw :size="16" /><b>重开工单</b></div><textarea v-model="action.reopenReason" rows="2" maxlength="1000" placeholder="重开原因（必填）" /><button class="button warning" :disabled="actionLoading || !action.reopenReason.trim()" @click="reopen">确认重开</button></div></div></section>

          <section class="timeline-section"><div class="timeline-title"><History :size="16" /><b>审计时间线</b><span>{{ detail.events.length }} 个事件</span></div><ol class="timeline"><li v-for="event in detail.events" :key="event.id"><span class="timeline-dot"><CircleDot :size="14" /></span><div><header><b>{{ event.eventType }}</b><time>{{ formatDate(event.createdAt) }}</time></header><p>{{ event.note || '状态记录' }}</p><footer><span>{{ event.operator || 'system' }} · {{ event.actorRole }}</span><span v-if="event.fromStatus || event.toStatus">{{ event.fromStatus ? statusLabels[event.fromStatus] : '创建' }} → {{ event.toStatus ? statusLabels[event.toStatus] : '—' }}</span></footer></div></li></ol></section>
        </div>
      </template>
      <div v-else class="ticket-placeholder"><ClipboardList :size="39" /><span class="eyebrow">TICKET LIFECYCLE</span><h2>选择一条工单查看完整生命周期</h2><p>状态流转会校验处理结论与关闭原因；并发更新通过版本字段拒绝覆盖。</p></div>
    </section>
  </div>
</template>

<style scoped>
.ticket-layout { height: calc(100vh - 149px); min-height: 570px; display: grid; grid-template-columns: 360px minmax(500px, 1fr); gap: 12px; }.ticket-list-panel, .ticket-detail-panel { min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.ticket-filters { display: grid; grid-template-columns: 1fr 105px 105px; gap: 6px; padding: 8px; border-bottom: 1px solid var(--line); }.search-field { position: relative; }.search-field svg { position: absolute; left: 9px; top: 50%; transform: translateY(-50%); color: var(--muted); }.search-field input { padding-left: 30px; }.ticket-filters input, .ticket-filters select { height: 34px; padding-top: 6px; padding-bottom: 6px; font-size: 10px; }
.ticket-alert, .detail-notice { display: flex; align-items: center; gap: 7px; padding: 7px 10px; color: #842b2b; background: var(--red-soft); border-bottom: 1px solid #d7a19a; font-size: 10px; }.ticket-alert button, .detail-notice button { margin-left: auto; color: inherit; background: transparent; text-decoration: underline; }.detail-notice { color: #164f4a; background: var(--teal-soft); border-color: #9fc8c1; }
.ticket-list { flex: 1; overflow: auto; padding: 7px; }.ticket-row { width: 100%; display: block; padding: 10px; text-align: left; color: var(--ink); background: transparent; border: 1px solid transparent; border-bottom-color: var(--line); }.ticket-row:hover { background: #f2f0e9; }.ticket-row.selected { background: var(--teal-soft); border-color: #9dc8c1; }.ticket-row.urgent:not(.selected) { border-left: 3px solid var(--red); }.ticket-row > div { display: flex; justify-content: space-between; align-items: center; gap: 8px; }.ticket-row h3 { margin: 7px 0 5px; font-size: 12px; line-height: 1.45; }.ticket-row p { display: flex; gap: 5px; margin: 0; }.ticket-row p span { padding: 2px 5px; color: #565c57; background: #eceae4; font-size: 9px; }.ticket-row p span.urgent { color: #8b2525; background: var(--red-soft); }.ticket-row footer { display: flex; justify-content: space-between; color: var(--muted); margin-top: 7px; font-size: 9px; }.pagination { min-height: 45px; display: flex; justify-content: center; align-items: center; gap: 10px; border-top: 1px solid var(--line); }.pagination .icon-button { width: 30px; height: 30px; }.pagination span { color: var(--muted); font-size: 10px; }
.ticket-detail-head { display: flex; justify-content: space-between; gap: 15px; padding: 16px 18px 13px; border-bottom: 1px solid var(--line); }.detail-badges { display: flex; gap: 5px; margin-bottom: 8px; }.ticket-number { color: var(--teal); font-size: 11px; }.ticket-detail-head h2 { margin: 3px 0 0; font-size: 19px; }.sla-card { min-width: 175px; display: flex; gap: 8px; align-items: center; padding: 9px 10px; background: #eeece5; border: 1px solid var(--line); }.sla-card span, .sla-card small, .sla-card b, .sla-card em { display: block; }.sla-card small { color: var(--muted); font: 9px 'IBM Plex Mono', monospace; }.sla-card b { font-size: 12px; margin: 2px 0; }.sla-card em { color: var(--muted); font-size: 9px; font-style: normal; }.sla-card.success { color: var(--teal-dark); background: var(--teal-soft); border-color: #9ec7c1; }.sla-card.warning { color: #824200; background: var(--amber-soft); border-color: #d9aa75; }.sla-card.danger { color: #8a2929; background: var(--red-soft); border-color: #d5a19b; }.sla-track { height: 3px; background: #dbd8d0; }.sla-track span { display: block; height: 100%; background: var(--line-strong); }.sla-track span.success { background: var(--teal); }.sla-track span.warning { background: var(--amber); }.sla-track span.danger { background: var(--red); }
.ticket-detail-scroll { flex: 1; overflow: auto; }.ticket-summary { padding: 15px 18px; border-bottom: 1px solid var(--line); }.ticket-summary > dl { display: grid; grid-template-columns: repeat(3, 1fr); margin: 0 0 14px; border: 1px solid var(--line); }.ticket-summary > dl div { padding: 8px 9px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }.ticket-summary > dl div:nth-child(3n) { border-right: 0; }.ticket-summary > dl div:nth-last-child(-n+3) { border-bottom: 0; }.ticket-summary dt { color: var(--muted); font-size: 9px; }.ticket-summary dd { margin: 3px 0 0; font-size: 11px; }.description, .resolution { padding: 10px; background: #f0eee7; border: 1px solid var(--line); }.description p, .resolution p { margin: 4px 0 0; white-space: pre-wrap; line-height: 1.6; font-size: 11px; }.resolution { margin-top: 7px; background: var(--teal-soft); border-color: #a2cac3; }
.ticket-actions { padding: 15px 18px; border-bottom: 1px solid var(--line); }.action-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }.action-box { align-content: start; display: grid; gap: 7px; padding: 10px; background: #f1efe8; border: 1px solid var(--line); }.action-box > div { display: flex; align-items: center; gap: 6px; }.action-box > div b { font-size: 11px; }.action-box input, .action-box select, .action-box textarea { font-size: 10px; }.action-box textarea { min-height: 50px; }
.timeline-section { padding: 15px 18px 22px; }.timeline-title { display: flex; align-items: center; gap: 6px; }.timeline-title b { font-size: 12px; }.timeline-title span { color: var(--muted); margin-left: auto; font-size: 9px; }.timeline { padding: 0; margin: 14px 0 0; list-style: none; }.timeline li { display: grid; grid-template-columns: 26px 1fr; }.timeline-dot { display: flex; justify-content: center; color: var(--teal); position: relative; }.timeline li:not(:last-child) .timeline-dot::after { content: ''; position: absolute; top: 16px; bottom: 0; width: 1px; background: var(--line-strong); }.timeline li > div { padding: 0 0 16px; }.timeline header, .timeline footer { display: flex; justify-content: space-between; gap: 8px; }.timeline header b { font: 600 10px 'IBM Plex Mono', monospace; }.timeline header time, .timeline footer { color: var(--muted); font-size: 9px; }.timeline p { margin: 4px 0; font-size: 11px; }.ticket-placeholder { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 30px; }.ticket-placeholder svg { color: var(--teal); margin-bottom: 12px; }.ticket-placeholder h2 { font-size: 19px; margin: 5px 0 8px; }.ticket-placeholder p { max-width: 420px; color: var(--muted); line-height: 1.6; font-size: 11px; }
@media (max-width: 1050px) { .ticket-layout { grid-template-columns: 310px minmax(450px, 1fr); }.action-grid { grid-template-columns: 1fr; }.ticket-summary > dl { grid-template-columns: repeat(2, 1fr); }.ticket-summary > dl div:nth-child(3n) { border-right: 1px solid var(--line); }.ticket-summary > dl div:nth-child(2n) { border-right: 0; }.ticket-summary > dl div:nth-last-child(-n+3) { border-bottom: 1px solid var(--line); }.ticket-summary > dl div:nth-last-child(-n+2) { border-bottom: 0; } }
@media (max-width: 760px) { .ticket-layout { height: auto; min-height: calc(100dvh - 126px); grid-template-columns: 1fr; grid-template-rows: 390px minmax(620px, auto); }.ticket-list-panel { min-height: 390px; }.ticket-detail-panel { min-height: 620px; }.ticket-detail-head { flex-direction: column; }.sla-card { width: 100%; }.ticket-filters { grid-template-columns: 1fr 95px; }.ticket-filters select:last-child { display: none; } }
</style>
