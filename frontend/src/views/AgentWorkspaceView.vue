<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Bot, CheckCircle2, Clock3, Headphones, Inbox, RefreshCw,
  Send, ShieldAlert, UserRound, Wifi, WifiOff
} from '@lucide/vue'
import { api, RealtimeClient, type RealtimeState } from '../api'
import { sessionState } from '../session'
import type { Conversation, ConversationMessage, PageResult, RealtimeEnvelope, Ticket } from '../types'
import { clientId, errorMessage, formatDate, priorityLabels, statusLabels, statusTone } from '../ui'

type Tab = 'queue' | 'assigned'
interface ClaimResult { conversation: Conversation; ticket: Ticket | null }

const tab = ref<Tab>('queue')
const rows = ref<Conversation[]>([])
const selected = ref<Conversation | null>(null)
const messages = ref<ConversationMessage[]>([])
const linkedTicket = ref<Ticket | null>(null)
const reply = ref('')
const loading = ref(true)
const detailLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const notice = ref('')
const realtimeState = ref<RealtimeState>('connecting')
const scroller = ref<HTMLElement | null>(null)
let realtime: RealtimeClient | null = null

const queueCount = computed(() => tab.value === 'queue' ? rows.value.length : 0)
const canReply = computed(() => selected.value?.status === 'HUMAN' && selected.value.assignedAgent === sessionState.user?.username)
const lastMessageId = computed(() => Math.max(0, ...messages.value.map((item) => item.id)))

onMounted(async () => {
  await loadRows()
  const username = sessionState.user?.username
  if (username) {
    realtime = new RealtimeClient(username, handleRealtime, (state) => { realtimeState.value = state })
    realtime.connect()
  }
})
onBeforeUnmount(() => realtime?.close())

async function switchTab(next: Tab) {
  tab.value = next
  selected.value = null
  messages.value = []
  linkedTicket.value = null
  await loadRows()
}

async function loadRows(keepId?: number) {
  loading.value = true
  error.value = ''
  try {
    const page = await api.get<PageResult<Conversation>>(`/api/conversations?mode=${tab.value}&page=1&size=100`, { dedupe: false })
    rows.value = page.records
    const id = keepId ?? selected.value?.id
    if (id) {
      const row = page.records.find((item) => item.id === id)
      if (row) selected.value = row
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function openConversation(conversation: Conversation) {
  selected.value = conversation
  detailLoading.value = true
  error.value = ''
  try {
    const [history, ticketPage] = await Promise.all([
      api.get<ConversationMessage[]>(`/api/conversations/${conversation.id}/messages?afterId=0&limit=300`, { dedupe: false }),
      api.get<PageResult<Ticket>>('/api/tickets?page=1&size=100', { dedupe: false })
    ])
    messages.value = history
    linkedTicket.value = ticketPage.records.find((item) => item.conversationId === conversation.id) || null
    if (lastMessageId.value) await markRead(lastMessageId.value)
    await scrollDown(false)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    detailLoading.value = false
  }
}

async function claim() {
  if (!selected.value || actionLoading.value) return
  actionLoading.value = true
  error.value = ''
  try {
    const result = await api.post<ClaimResult>(`/api/conversations/${selected.value.id}/claim`)
    selected.value = result.conversation
    linkedTicket.value = result.ticket
    notice.value = '会话已原子认领，机器人已暂停；现在可直接回复客户。'
    tab.value = 'assigned'
    await loadRows(result.conversation.id)
    await openConversation(result.conversation)
  } catch (reason) {
    error.value = errorMessage(reason)
    await loadRows()
  } finally {
    actionLoading.value = false
  }
}

async function sendReply() {
  const content = reply.value.trim()
  if (!selected.value || !content || !canReply.value || actionLoading.value) return
  actionLoading.value = true
  error.value = ''
  const key = clientId('agent')
  try {
    const result = await api.post<{ message: ConversationMessage; replayed: boolean }>(
      `/api/conversations/${selected.value.id}/messages`, { clientMessageId: key, content }
    )
    if (!messages.value.some((item) => item.id === result.message.id)) messages.value.push(result.message)
    reply.value = ''
    notice.value = result.replayed ? '检测到重复提交，已返回原消息。' : '回复已实时送达客户。'
    await scrollDown()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    actionLoading.value = false
  }
}

async function resolveTicket() {
  if (!linkedTicket.value || actionLoading.value) return
  actionLoading.value = true
  error.value = ''
  try {
    linkedTicket.value = await api.post<Ticket>(`/api/tickets/${linkedTicket.value.id}/transition`, {
      toStatus: 'RESOLVED', note: '坐席已在会话中完成处理，等待客户评价确认关闭。', closeReason: null
    })
    notice.value = '工单已标记为“已解决”，客户可评价并关闭服务闭环。'
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    actionLoading.value = false
  }
}

async function resumeBot() {
  if (!selected.value || actionLoading.value) return
  actionLoading.value = true
  error.value = ''
  try {
    selected.value = await api.post<Conversation>(`/api/conversations/${selected.value.id}/bot/resume`, {
      note: '坐席确认无需继续人工接管，恢复机器人服务。'
    })
    notice.value = '机器人服务已恢复，会话已从当前坐席列表移除。'
    await loadRows()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    actionLoading.value = false
  }
}

async function markRead(upToId: number) {
  if (!selected.value) return
  await api.post(`/api/conversations/${selected.value.id}/read`, { upToId }).catch(() => undefined)
}

function handleRealtime(event: RealtimeEnvelope) {
  const payload = event.payload as Record<string, unknown>
  const conversationId = Number(payload.conversationId || payload.id || 0)
  if (event.type === 'message.customer' && conversationId === selected.value?.id) {
    const message = payload as unknown as ConversationMessage
    if (!messages.value.some((item) => item.id === message.id)) messages.value.push(message)
    void markRead(message.id)
    void scrollDown()
  }
  if (event.type === 'message.agent' && conversationId === selected.value?.id) {
    const message = payload as unknown as ConversationMessage
    if (!messages.value.some((item) => item.id === message.id)) messages.value.push(message)
  }
  if (event.type === 'conversation.handoff' && tab.value === 'queue') notice.value = '有新的客户进入人工队列。'
  if (event.type === 'conversation.claimed' && tab.value === 'queue' && conversationId === selected.value?.id) {
    notice.value = '此会话已被其他坐席认领，队列已刷新。'
    selected.value = null
  }
  if (event.type === 'conversation.closed' && conversationId === selected.value?.id && selected.value) {
    selected.value.status = 'CLOSED'
    notice.value = '客户已完成评价，会话与关联工单已关闭。'
  }
  if (event.type.startsWith('ticket.') && linkedTicket.value && Number(payload.id) === linkedTicket.value.id) {
    // The event is emitted inside the ticket transaction. Its payload is the committed
    // target state, while an immediate follow-up GET can still observe the previous state.
    linkedTicket.value = payload as unknown as Ticket
  }
  void loadRows(selected.value?.id).catch(() => undefined)
}

function messageLabel(message: ConversationMessage) {
  if (message.role === 'user') return selected.value?.userName || '客户'
  if (message.role === 'agent') return message.senderName || '人工坐席'
  if (message.role === 'assistant') return '机器人'
  if (message.role === 'tool') return `工具 · ${message.toolName || ''}`
  return '系统'
}
async function scrollDown(smooth = true) {
  await nextTick()
  scroller.value?.scrollTo({ top: scroller.value.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
}
</script>

<template>
  <div class="workspace-layout">
    <section class="queue-panel panel">
      <header class="panel-header"><div><span class="panel-index">INBOX</span><h2>人工服务队列</h2></div><button class="icon-button" aria-label="刷新队列" :disabled="loading" @click="loadRows()"><RefreshCw /></button></header>
      <div class="queue-tabs"><button :class="{ active: tab === 'queue' }" @click="switchTab('queue')"><Inbox :size="15" />待认领 <span>{{ queueCount }}</span></button><button :class="{ active: tab === 'assigned' }" @click="switchTab('assigned')"><Headphones :size="15" />我处理中</button></div>
      <div v-if="loading" class="loading-line" />
      <div class="queue-list">
        <button v-for="conversation in rows" :key="conversation.id" class="queue-row" :class="{ selected: selected?.id === conversation.id }" @click="openConversation(conversation)">
          <div class="queue-row-top"><b>{{ conversation.userName }}</b><span class="badge" :class="statusTone(conversation.status)">{{ statusLabels[conversation.status] }}</span></div>
          <p>{{ conversation.handoffReason || '客户请求人工协助' }}</p>
          <footer><span class="mono">#{{ conversation.id }}</span><span>{{ formatDate(conversation.lastMessageAt) }}</span></footer>
        </button>
        <div v-if="!loading && !rows.length" class="empty-state">{{ tab === 'queue' ? '当前没有等待认领的会话' : '当前没有由你处理的会话' }}</div>
      </div>
    </section>

    <section class="service-panel panel">
      <template v-if="selected">
        <header class="service-head">
          <div><div class="customer-title"><UserRound :size="18" /><b>{{ selected.userName }}</b><span class="mono">#{{ selected.id }}</span><span class="badge" :class="statusTone(selected.status)">{{ statusLabels[selected.status] }}</span></div><p>{{ selected.handoffReason || '人工服务' }} · 最近意图 {{ selected.lastIntent || '未识别' }} · 情绪 {{ selected.lastSentiment || '中性' }}</p></div>
          <span class="realtime-badge" :class="realtimeState"><Wifi v-if="realtimeState === 'live'" :size="14" /><WifiOff v-else :size="14" />{{ realtimeState === 'live' ? '实时在线' : '正在重连' }}</span>
        </header>
        <div v-if="detailLoading" class="loading-line" />
        <div v-if="error" class="workspace-alert danger"><ShieldAlert :size="16" />{{ error }}<button @click="error = ''">关闭</button></div>
        <div v-if="notice" class="workspace-alert info"><CheckCircle2 :size="16" />{{ notice }}<button @click="notice = ''">关闭</button></div>
        <div class="service-inline-actions">
          <button v-if="selected.status === 'HUMAN_PENDING'" class="button primary" :disabled="actionLoading" @click="claim"><Headphones :size="15" />认领并接管</button>
          <button v-if="canReply && linkedTicket && !['RESOLVED','CLOSED'].includes(linkedTicket.status)" class="button primary" :disabled="actionLoading" @click="resolveTicket"><CheckCircle2 :size="15" />标记工单已解决</button>
          <button v-if="canReply" class="button" :disabled="actionLoading" @click="resumeBot"><Bot :size="15" />恢复机器人</button>
          <span v-if="linkedTicket" class="mono">{{ linkedTicket.ticketNo }} · {{ statusLabels[linkedTicket.status] }}</span>
        </div>
        <div ref="scroller" class="agent-messages">
          <article v-for="message in messages" :key="message.id" class="agent-message" :class="message.role">
            <div class="agent-message-meta"><b>{{ messageLabel(message) }}</b><span v-if="message.intent">{{ message.intent }} · {{ message.sentiment }}</span><time>{{ formatDate(message.createdAt) }}</time></div>
            <p>{{ message.content }}</p>
            <small v-if="message.deliveryStatus">{{ message.readAt ? `已读 ${formatDate(message.readAt)}` : message.deliveryStatus }}</small>
          </article>
        </div>
        <footer class="agent-composer">
          <textarea v-model="reply" rows="2" maxlength="4000" :disabled="!canReply || actionLoading" :placeholder="canReply ? '输入对客户的回复，Ctrl + Enter 发送' : selected.status === 'HUMAN_PENDING' ? '认领后才能回复客户' : '该会话当前不可回复'" @keydown.ctrl.enter.prevent="sendReply" />
          <div><span>{{ reply.length }} / 4000</span><button class="button primary" :disabled="!canReply || !reply.trim() || actionLoading" @click="sendReply"><Send :size="15" />发送给客户</button></div>
        </footer>
      </template>
      <div v-else class="workspace-placeholder"><Headphones :size="38" /><span class="eyebrow">SERVICE DESK</span><h2>从左侧选择客户会话</h2><p>待认领会话使用数据库条件更新避免多人抢单；认领成功后机器人立即暂停。</p></div>
    </section>

    <aside class="context-panel panel">
      <header class="panel-header"><div><span class="panel-index">CONTEXT</span><h2>处置上下文</h2></div></header>
      <template v-if="selected">
        <section class="context-section"><div class="section-label">CUSTOMER</div><dl><div><dt>客户</dt><dd>{{ selected.userName }}</dd></div><div><dt>账号</dt><dd class="mono">{{ selected.customerUsername }}</dd></div><div><dt>渠道</dt><dd>{{ selected.channel }}</dd></div><div><dt>坐席</dt><dd>{{ selected.assignedAgent || '未分配' }}</dd></div></dl></section>
        <section class="context-section"><div class="section-label">LINKED TICKET</div><div v-if="linkedTicket" class="linked-ticket"><div><b class="mono">{{ linkedTicket.ticketNo }}</b><span class="badge" :class="statusTone(linkedTicket.status)">{{ statusLabels[linkedTicket.status] }}</span></div><p>{{ linkedTicket.title }}</p><dl><div><dt>优先级</dt><dd>{{ priorityLabels[linkedTicket.priority] }}</dd></div><div><dt>SLA 到期</dt><dd>{{ formatDate(linkedTicket.slaDueAt) }}</dd></div></dl></div><div v-else class="empty-state">未找到关联工单</div></section>
        <section class="context-section actions"><div class="section-label">ACTIONS</div><button v-if="selected.status === 'HUMAN_PENDING'" class="button primary" :disabled="actionLoading" @click="claim"><Headphones :size="15" />{{ actionLoading ? '认领中…' : '认领并接管' }}</button><button v-if="canReply && linkedTicket && !['RESOLVED','CLOSED'].includes(linkedTicket.status)" class="button primary" :disabled="actionLoading" @click="resolveTicket"><CheckCircle2 :size="15" />标记工单已解决</button><button v-if="canReply" class="button" :disabled="actionLoading" @click="resumeBot"><Bot :size="15" />结束人工并恢复机器人</button></section>
        <section class="handoff-facts"><Clock3 :size="16" /><p>认领、回复、已读、工单流转和恢复机器人均生成实时事件；敏感字段不会写入前端日志。</p></section>
      </template>
      <div v-else class="empty-state context-empty">选择会话后显示客户、关联工单与处置动作</div>
    </aside>
  </div>
</template>

<style scoped>
.workspace-layout { height: calc(100vh - 149px); min-height: 560px; display: grid; grid-template-columns: 280px minmax(430px, 1fr) 280px; gap: 12px; }.queue-panel, .service-panel, .context-panel { min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.queue-tabs { display: grid; grid-template-columns: 1fr 1fr; border-bottom: 1px solid var(--line); }.queue-tabs button { display: flex; align-items: center; justify-content: center; gap: 6px; padding: 9px 5px; color: var(--muted); background: #eeece5; border-bottom: 2px solid transparent; font-size: 11px; }.queue-tabs button.active { color: var(--teal-dark); background: var(--surface); border-bottom-color: var(--teal); }.queue-tabs button span { min-width: 18px; padding: 1px 4px; color: #fff; background: var(--amber); font: 9px 'IBM Plex Mono', monospace; }
.queue-list { flex: 1; overflow: auto; padding: 7px; }.queue-row { width: 100%; text-align: left; padding: 10px; color: var(--ink); background: transparent; border: 1px solid transparent; border-bottom-color: var(--line); }.queue-row:hover { background: #f2f0e9; }.queue-row.selected { background: var(--teal-soft); border-color: #9dc8c1; }.queue-row-top { display: flex; justify-content: space-between; gap: 7px; align-items: center; }.queue-row-top b { font-size: 12px; }.queue-row p { margin: 6px 0; color: #565c57; line-height: 1.45; font-size: 11px; }.queue-row footer { display: flex; justify-content: space-between; color: var(--muted); font-size: 9px; }
.service-head { min-height: 61px; display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 10px 13px; border-bottom: 1px solid var(--line); }.customer-title { display: flex; align-items: center; gap: 7px; }.customer-title b { font-size: 14px; }.customer-title .mono { color: var(--muted); font-size: 10px; }.service-head p { margin: 4px 0 0; color: var(--muted); font-size: 10px; }.realtime-badge { display: flex; align-items: center; gap: 5px; color: var(--amber); font-size: 10px; }.realtime-badge.live { color: var(--teal); }
.workspace-alert { display: flex; gap: 7px; align-items: center; padding: 7px 10px; border-bottom: 1px solid; font-size: 10px; }.workspace-alert svg { flex: 0 0 auto; }.workspace-alert button { margin-left: auto; color: inherit; background: transparent; text-decoration: underline; }.workspace-alert.danger { color: #842b2b; background: var(--red-soft); border-color: #d7a19a; }.workspace-alert.info { color: #164f4a; background: var(--teal-soft); border-color: #9fc8c1; }
.service-inline-actions { display: none; gap: 7px; align-items: center; padding: 7px 10px; background: #efede6; border-bottom: 1px solid var(--line); }.service-inline-actions > span { color: var(--muted); margin-left: auto; font-size: 9px; }
.agent-messages { flex: 1; overflow: auto; padding: 14px; }.agent-message { max-width: 82%; margin: 0 0 14px; }.agent-message.user { margin-right: auto; }.agent-message.agent { margin-left: auto; }.agent-message.assistant, .agent-message.tool, .agent-message.system { max-width: 92%; margin-left: auto; margin-right: auto; }.agent-message-meta { display: flex; gap: 7px; align-items: center; margin-bottom: 4px; font-size: 9px; }.agent-message-meta span, .agent-message-meta time { color: var(--muted); }.agent-message-meta time { margin-left: auto; }.agent-message p { margin: 0; padding: 9px 10px; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; font-size: 12px; background: #f0eee7; border: 1px solid var(--line); }.agent-message.agent p { color: #fff; background: #3c443f; border-color: #3c443f; }.agent-message.assistant p { background: var(--teal-soft); border-color: #a0c9c2; }.agent-message.tool p { font-family: 'IBM Plex Mono', monospace; font-size: 10px; background: #f5faf8; border-style: dashed; }.agent-message small { display: block; color: var(--muted); margin-top: 3px; text-align: right; font-size: 8px; }
.agent-composer { padding: 9px 11px; background: #efede6; border-top: 1px solid var(--line); }.agent-composer textarea { min-height: 56px; max-height: 130px; }.agent-composer > div { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 6px; }.agent-composer > div span { color: var(--muted); margin-right: auto; font: 9px 'IBM Plex Mono', monospace; }
.workspace-placeholder { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 30px; }.workspace-placeholder svg { color: var(--teal); margin-bottom: 12px; }.workspace-placeholder h2 { margin: 5px 0 8px; font-size: 19px; }.workspace-placeholder p { max-width: 410px; color: var(--muted); line-height: 1.6; font-size: 12px; }
.context-panel { overflow: auto; }.context-section { padding: 13px; border-bottom: 1px solid var(--line); }.context-section dl { margin: 0; }.context-section dl div { display: grid; grid-template-columns: 75px 1fr; gap: 7px; padding: 5px 0; border-bottom: 1px dashed var(--line); font-size: 10px; }.context-section dl div:last-child { border-bottom: 0; }.context-section dt { color: var(--muted); }.context-section dd { margin: 0; text-align: right; overflow-wrap: anywhere; }.linked-ticket > div { display: flex; justify-content: space-between; gap: 7px; }.linked-ticket p { margin: 8px 0; line-height: 1.5; font-size: 11px; }.actions { display: grid; gap: 7px; }.actions .section-label { margin-bottom: 1px; }.handoff-facts { display: flex; gap: 8px; padding: 12px; color: #555b56; background: #edebe4; }.handoff-facts p { margin: 0; line-height: 1.55; font-size: 10px; }.context-empty { margin: 13px; }
@media (max-width: 1180px) { .workspace-layout { grid-template-columns: 260px minmax(420px, 1fr); }.context-panel { display: none; }.service-inline-actions { display: flex; } }
@media (max-width: 760px) { .workspace-layout { height: auto; min-height: calc(100dvh - 126px); grid-template-columns: 1fr; grid-template-rows: 250px minmax(520px, 1fr); }.queue-panel { min-height: 250px; }.service-panel { min-height: 520px; }.service-head p { display: none; }.agent-message { max-width: 94%; }.service-inline-actions { flex-wrap: wrap; }.service-inline-actions > span { width: 100%; margin-left: 0; } }
</style>
