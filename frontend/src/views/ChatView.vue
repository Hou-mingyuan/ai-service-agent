<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  AlertTriangle, Bot, Check, CheckCircle2, ChevronRight, CircleStop, Clock3, Database, FileText,
  Headphones, MessageSquarePlus, PackageSearch, RefreshCw, Send, ShieldCheck, Sparkles,
  Star, UserRound, Wrench
} from '@lucide/vue'
import { api, chatStream, RealtimeClient, type RealtimeState } from '../api'
import { sessionState } from '../session'
import type {
  Conversation, ConversationMessage, KnowledgeSearchResult, OrderData, PageResult, PolicyData,
  RealtimeEnvelope, ToolExecution, ToolResult
} from '../types'
import { clientId, conversationActionsDisabled, errorMessage, formatDate, statusLabels, statusTone } from '../ui'

interface ToolStep {
  name: string
  arguments: Record<string, unknown>
  pending: boolean
  ok?: boolean
  summary?: string
  status?: string
  executionId?: number
  dataSource?: string
}

interface DisplayMessage {
  key: string
  id?: number
  role: 'user' | 'assistant' | 'agent' | 'tool' | 'system'
  sender?: string
  content: string
  createdAt?: string
  pending?: boolean
  meta?: { intent: string; intentLabel: string; sentiment: string; sentimentLabel: string; confidence: number }
  tools?: ToolStep[]
  citations?: KnowledgeSearchResult[]
  handoff?: { ticketNo: string; ticketId: number; priority: string; reason: string }
}

const conversations = ref<Conversation[]>([])
const current = ref<Conversation | null>(null)
const messages = ref<DisplayMessage[]>([])
const orders = ref<OrderData[]>([])
const policies = ref<PolicyData[]>([])
const executions = ref<ToolExecution[]>([])
const input = ref('')
const loading = ref(true)
const historyLoading = ref(false)
const sending = ref(false)
const error = ref('')
const notice = ref('')
const scroller = ref<HTMLElement | null>(null)
const realtimeState = ref<RealtimeState>('connecting')
const confirmTarget = ref<ToolExecution | null>(null)
const confirming = ref(false)
const feedback = reactive({ visible: false, rating: 0, comment: '', sending: false, done: false })
let abortController: AbortController | null = null
let realtime: RealtimeClient | null = null

const quickPrompts = [
  '帮我查询订单 123',
  '订单 123 的物流到哪里了？',
  '查询保单 PAI2024001',
  '订单 1003 改期到 2026-08-15',
  '这个处理让我很不满意，我要投诉并转人工'
]
const toolLabels: Record<string, string> = {
  query_order: '订单查询', query_logistics: '物流查询', query_policy: '保单查询',
  create_ticket: '创建工单', query_ticket: '工单查询', reschedule_appointment: '业务改期'
}
const modeLabel = computed(() => statusLabels[current.value?.status || 'BOT'])
const isHuman = computed(() => ['HUMAN_PENDING', 'HUMAN'].includes(current.value?.status || ''))
const actionsDisabled = computed(() => conversationActionsDisabled(current.value?.status, sending.value))
const canSend = computed(() => !!input.value.trim() && !actionsDisabled.value)
const isDemo = computed(() => sessionState.health?.mode === 'DEMO')
const sourceText = computed(() => isDemo.value ? 'Mock 业务数据 / Mock LLM' : `${sessionState.health?.business.source} / ${sessionState.health?.llm.provider}`)
const pendingExecutions = computed(() => executions.value.filter((item) => item.status === 'PENDING_CONFIRMATION'))
const completedSensitiveExecutions = computed(() => executions.value.filter((item) => item.sensitive === 1 && item.status === 'COMPLETED'))

onMounted(async () => {
  await loadInitial()
  const username = sessionState.user?.username
  if (username) {
    realtime = new RealtimeClient(username, handleRealtime, (state) => { realtimeState.value = state })
    realtime.connect()
  }
})
onBeforeUnmount(() => {
  abortController?.abort()
  realtime?.close()
})

async function loadInitial() {
  loading.value = true
  error.value = ''
  try {
    const [page, orderRows, policyRows] = await Promise.all([
      api.get<PageResult<Conversation>>('/api/conversations?mode=mine&page=1&size=30'),
      api.get<OrderData[]>('/api/catalog/orders').catch(() => []),
      api.get<PolicyData[]>('/api/catalog/policies').catch(() => [])
    ])
    conversations.value = page.records
    orders.value = orderRows
    policies.value = policyRows
    if (page.records.length) await selectConversation(page.records[0])
    else startNew()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function refreshConversations(selectId?: number) {
  const page = await api.get<PageResult<Conversation>>('/api/conversations?mode=mine&page=1&size=30', { dedupe: false })
  conversations.value = page.records
  const id = selectId ?? current.value?.id
  if (id) current.value = page.records.find((item) => item.id === id) || current.value
}

async function selectConversation(conversation: Conversation) {
  if (sending.value) return
  current.value = conversation
  historyLoading.value = true
  error.value = ''
  feedback.visible = false
  feedback.done = false
  try {
    const [history, tools] = await Promise.all([
      api.get<ConversationMessage[]>(`/api/conversations/${conversation.id}/messages?afterId=0&limit=200`, { dedupe: false }),
      api.get<ToolExecution[]>(`/api/tool-executions?conversationId=${conversation.id}`, { dedupe: false })
    ])
    messages.value = history.map(fromPersisted)
    executions.value = tools
    feedback.done = conversation.status === 'CLOSED'
    feedback.visible = feedback.done || (conversation.status === 'BOT' && history.some((item) => item.role === 'assistant'))
    await scrollDown(false)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    historyLoading.value = false
  }
}

function startNew() {
  if (sending.value) return
  current.value = null
  executions.value = []
  feedback.visible = false
  feedback.done = false
  messages.value = [{
    key: 'welcome', role: 'system', content: '请描述需要处理的问题。我会先检索知识与业务数据；低置信度、负面情绪或明确人工诉求会自动进入人工队列。'
  }]
}

function fromPersisted(message: ConversationMessage): DisplayMessage {
  return {
    key: `persisted-${message.id}`, id: message.id, role: message.role,
    sender: message.senderName || undefined, content: message.role === 'tool' ? '' : (message.content || ''), createdAt: message.createdAt,
    meta: message.intent ? {
      intent: message.intent, intentLabel: message.intent, sentiment: message.sentiment || 'NEUTRAL',
      sentimentLabel: message.sentiment || '中性', confidence: message.intentConfidence || 0
    } : undefined,
    tools: message.role === 'tool' ? [{
      name: message.toolName || 'tool', arguments: parseObject(message.toolArgs), pending: false,
      ok: true, summary: message.content
    }] : undefined
  }
}

async function send(text?: string) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value || current.value?.status === 'CLOSED') return
  input.value = ''
  error.value = ''
  notice.value = ''
  feedback.visible = false
  const idempotencyKey = clientId('chat')
  const optimistic: DisplayMessage = { key: idempotencyKey, role: 'user', content, pending: true }
  messages.value.push(optimistic)
  sending.value = true
  await scrollDown()

  if (isHuman.value && current.value) {
    try {
      const result = await api.post<{ message: ConversationMessage; replayed: boolean }>(
        `/api/conversations/${current.value.id}/messages`, { clientMessageId: idempotencyKey, content }
      )
      optimistic.pending = false
      optimistic.id = result.message.id
      optimistic.createdAt = result.message.createdAt
      notice.value = current.value.status === 'HUMAN_PENDING' ? '消息已送入队列，坐席认领后可见。' : '消息已送达当前坐席。'
    } catch (reason) {
      messages.value = messages.value.filter((item) => item.key !== idempotencyKey)
      error.value = errorMessage(reason)
    } finally {
      sending.value = false
      await refreshConversations().catch(() => undefined)
    }
    return
  }

  const assistant: DisplayMessage = { key: clientId('reply'), role: 'assistant', content: '', pending: true, tools: [], citations: [] }
  messages.value.push(assistant)
  abortController = new AbortController()
  try {
    await chatStream({ conversationId: current.value?.id, clientMessageId: idempotencyKey, message: content }, (event, data) => {
      handleSse(event, data, assistant, optimistic)
      void scrollDown()
    }, abortController.signal)
  } catch (reason) {
    if (!(reason instanceof DOMException && reason.name === 'AbortError')) {
      error.value = errorMessage(reason)
      assistant.content ||= '本轮响应未完成，可使用相同问题重试。'
    } else {
      notice.value = '已取消本轮生成；已提交的客户消息仍会保留。'
    }
  } finally {
    assistant.pending = false
    optimistic.pending = false
    if (!assistant.content && !assistant.tools?.length && !assistant.handoff) {
      messages.value = messages.value.filter((item) => item.key !== assistant.key)
    }
    sending.value = false
    abortController = null
    await refreshConversations(current.value?.id).catch(() => undefined)
    if (current.value) await refreshExecutions()
    await scrollDown()
  }
}

function handleSse(event: string, data: Record<string, unknown>, assistant: DisplayMessage, optimistic: DisplayMessage) {
  if (event === 'start') {
    const id = Number(data.conversationId)
    optimistic.id = Number(data.userMessageId)
    if (!current.value || current.value.id !== id) {
      current.value = {
        id, tenantId: '', sessionKey: String(data.sessionKey || ''), customerUsername: sessionState.user?.username || '',
        userName: sessionState.user?.displayName || '', channel: 'web', status: String(data.conversationStatus || 'BOT') as Conversation['status'],
        botEnabled: 1, assignedAgent: null, handoffReason: null, lastIntent: null, lastSentiment: null, resolved: 0,
        handoffAt: null, claimedAt: null, firstResponseAt: null, lastMessageAt: null, version: 0, createdAt: '', updatedAt: ''
      }
    }
  } else if (event === 'meta') {
    if (data.mode === 'HUMAN') return
    assistant.meta = {
      intent: String(data.intent || ''), intentLabel: String(data.intentLabel || data.intent || ''),
      sentiment: String(data.sentiment || ''), sentimentLabel: String(data.sentimentLabel || data.sentiment || ''),
      confidence: Number(data.intentConfidence || 0)
    }
  } else if (event === 'tool_call') {
    assistant.tools?.push({ name: String(data.name), arguments: (data.arguments || {}) as Record<string, unknown>, pending: true })
  } else if (event === 'tool_result') {
    const tool = [...(assistant.tools || [])].reverse().find((item) => item.name === data.name && item.pending)
    if (tool) Object.assign(tool, {
      pending: false, ok: Boolean(data.ok), summary: String(data.summary || ''), status: String(data.status || ''),
      executionId: data.executionId ? Number(data.executionId) : undefined, dataSource: String(data.dataSource || '')
    })
  } else if (event === 'citations') {
    assistant.citations = (data.items || []) as unknown as KnowledgeSearchResult[]
  } else if (event === 'token') {
    assistant.content += String(data.text || '')
  } else if (event === 'confirmation_required') {
    const executionId = Number(data.executionId)
    const tool = assistant.tools?.find((item) => item.executionId === executionId)
    if (tool) tool.status = 'PENDING_CONFIRMATION'
  } else if (event === 'handoff') {
    assistant.handoff = {
      ticketNo: String(data.ticketNo), ticketId: Number(data.ticketId), priority: String(data.priority), reason: String(data.reason)
    }
  } else if (event === 'done') {
    assistant.pending = false
    if (current.value) current.value.status = String(data.mode || current.value.status) as Conversation['status']
    feedback.visible = data.mode === 'BOT'
  } else if (event === 'error') {
    assistant.pending = false
    error.value = String(data.message || '对话处理失败')
  }
}

function cancelStream() { abortController?.abort() }

async function refreshExecutions() {
  if (!current.value) return
  executions.value = await api.get<ToolExecution[]>(`/api/tool-executions?conversationId=${current.value.id}`, { dedupe: false })
}

async function confirmExecution() {
  if (!confirmTarget.value) return
  confirming.value = true
  error.value = ''
  try {
    const result = await api.post<ToolResult>(`/api/tool-executions/${confirmTarget.value.id}/confirm`)
    notice.value = result.summary
    confirmTarget.value = null
    await refreshExecutions()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    confirming.value = false
  }
}

async function submitFeedback(rating: number) {
  if (!current.value || feedback.sending || feedback.done) return
  feedback.rating = rating
  feedback.sending = true
  error.value = ''
  try {
    await api.post('/api/feedback', { conversationId: current.value.id, rating, comment: feedback.comment.trim() || null })
    feedback.done = true
    current.value.status = 'CLOSED'
    notice.value = '评价已提交，会话与已解决工单已关闭。'
    await refreshConversations(current.value.id)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    feedback.sending = false
  }
}

function handleRealtime(event: RealtimeEnvelope) {
  const payload = event.payload as Record<string, unknown>
  const conversationId = Number(payload.conversationId || payload.id || 0)
  if (event.type === 'message.agent' && conversationId === current.value?.id) {
    const message = payload as unknown as ConversationMessage
    if (!messages.value.some((item) => item.id === message.id)) messages.value.push(fromPersisted(message))
    void markRead(message.id)
    void scrollDown()
  }
  if (event.type.startsWith('conversation.') && conversationId === current.value?.id && current.value) {
    if (payload.status) current.value.status = String(payload.status) as Conversation['status']
    if (payload.assignedAgent) current.value.assignedAgent = String(payload.assignedAgent)
    if (event.type === 'conversation.closed') {
      current.value.status = 'CLOSED'
      feedback.visible = true
      feedback.done = true
    }
    notice.value = event.type === 'conversation.claimed'
      ? `坐席 ${payload.assignedAgent} 已接管当前会话。`
      : event.type === 'conversation.bot_resumed' ? '人工处理结束，机器人服务已恢复。' : notice.value
  }
  if (event.type.startsWith('ticket.') && conversationId === current.value?.id) {
    const status = String(payload.status || '')
    if (status === 'RESOLVED') {
      feedback.visible = true
      notice.value = '坐席已解决关联工单，请评价本次服务后关闭会话。'
    }
  }
  void refreshConversations().catch(() => undefined)
}

async function markRead(messageId: number) {
  if (!current.value) return
  await api.post(`/api/conversations/${current.value.id}/read`, { upToId: messageId }).catch(() => undefined)
}

function fillOrder(orderNo: string) { input.value = `帮我查询订单 ${orderNo}` }
function fillPolicy(policyNo: string) { input.value = `查询保单 ${policyNo}` }
function parseObject(value?: string | null): Record<string, unknown> {
  try { return value ? JSON.parse(value) as Record<string, unknown> : {} } catch { return {} }
}
function visibleArguments(args: Record<string, unknown>) {
  return Object.entries(args).filter(([key]) => !['conversation_id', 'customer'].includes(key))
}
async function scrollDown(smooth = true) {
  await nextTick()
  scroller.value?.scrollTo({ top: scroller.value.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
}
</script>

<template>
  <div class="chat-layout">
    <aside class="conversation-panel panel">
      <header class="panel-header"><div><span class="panel-index">SESSIONS</span><h2>我的会话</h2></div><button class="button small" @click="startNew"><MessageSquarePlus :size="15" />新会话</button></header>
      <div v-if="loading" class="loading-line" />
      <div class="conversation-list">
        <button class="conversation-row new-row" :class="{ selected: !current }" @click="startNew"><Sparkles :size="17" /><span><b>开始新咨询</b><small>创建独立会话</small></span></button>
        <button v-for="conversation in conversations" :key="conversation.id" class="conversation-row" :class="{ selected: current?.id === conversation.id }" @click="selectConversation(conversation)">
          <span class="conversation-index mono">#{{ conversation.id }}</span>
          <span class="conversation-copy"><b>{{ conversation.lastIntent || '客户咨询' }}</b><small>{{ formatDate(conversation.lastMessageAt) }}</small></span>
          <span class="badge" :class="statusTone(conversation.status)">{{ statusLabels[conversation.status] }}</span>
        </button>
      </div>
      <div class="conversation-source"><Database :size="15" /><span><b>{{ isDemo ? '演示边界' : '数据来源' }}</b>{{ sourceText }}</span></div>
    </aside>

    <section class="chat-panel panel">
      <header class="chat-header">
        <div><div class="chat-title"><Bot :size="18" /><b>{{ current ? `会话 #${current.id}` : '新客户咨询' }}</b><span class="badge" :class="statusTone(current?.status || 'BOT')">{{ modeLabel }}</span></div><p v-if="current">{{ current.sessionKey }} · {{ current.assignedAgent ? `坐席 ${current.assignedAgent}` : '尚未分配坐席' }}</p><p v-else>新消息发送后生成会话编号</p></div>
        <span class="live-state" :class="realtimeState"><span />{{ realtimeState === 'live' ? '实时在线' : realtimeState === 'offline' ? '网络离线' : '正在连接' }}</span>
      </header>

      <div v-if="historyLoading" class="loading-line" />
      <div v-if="error" class="chat-alert danger"><AlertTriangle :size="16" /><span>{{ error }}</span><button @click="error = ''">关闭</button></div>
      <div v-if="notice" class="chat-alert info"><ShieldCheck :size="16" /><span>{{ notice }}</span><button @click="notice = ''">关闭</button></div>

      <div ref="scroller" class="message-stream" aria-live="polite">
        <article v-for="message in messages" :key="message.key" class="message-row" :class="message.role">
          <div class="message-avatar"><UserRound v-if="message.role === 'user'" :size="17" /><Headphones v-else-if="message.role === 'agent'" :size="17" /><Wrench v-else-if="message.role === 'tool'" :size="17" /><Bot v-else :size="17" /></div>
          <div class="message-body">
            <div class="message-author"><b>{{ message.role === 'user' ? '客户' : message.role === 'agent' ? (message.sender || '人工坐席') : message.role === 'assistant' ? '智答 Agent' : message.role === 'tool' ? '工具执行' : '系统提示' }}</b><time>{{ formatDate(message.createdAt) }}</time><span v-if="message.pending">发送中</span></div>
            <div v-if="message.meta" class="classification-strip"><span>意图 <b>{{ message.meta.intentLabel }}</b></span><span>置信度 <b>{{ Math.round(message.meta.confidence * 100) }}%</b></span><span>情绪 <b>{{ message.meta.sentimentLabel }}</b></span></div>
            <div v-for="(tool, index) in message.tools" :key="`${tool.name}-${index}`" class="tool-step">
              <div><Wrench :size="15" /><b>{{ toolLabels[tool.name] || tool.name }}</b><span class="badge" :class="tool.pending ? 'info' : tool.ok ? 'success' : 'warning'">{{ tool.pending ? '调用中' : tool.status === 'PENDING_CONFIRMATION' ? '等待确认' : tool.ok ? '已完成' : '未执行' }}</span></div>
              <dl v-if="visibleArguments(tool.arguments).length"><template v-for="([key, value]) in visibleArguments(tool.arguments)" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl>
              <p v-if="tool.summary">{{ tool.summary }}</p><small v-if="tool.dataSource">来源：{{ tool.dataSource }}</small>
            </div>
            <p v-if="message.content" class="message-bubble">{{ message.content }}<span v-if="message.pending && message.role === 'assistant'" class="typing-caret" /></p>
            <p v-else-if="message.pending" class="message-bubble typing"><span />正在分析请求并核对数据</p>
            <div v-if="message.citations?.length" class="citation-list"><div class="citation-title"><FileText :size="14" />知识来源</div><a v-for="citation in message.citations" :key="citation.chunkId" :href="citation.sourceUri || undefined" :target="citation.sourceUri ? '_blank' : undefined"><b>[{{ citation.citation }}] {{ citation.title }}</b><span>{{ citation.excerpt }}</span><small>相关度 {{ Math.round(citation.score * 100) }}% · {{ citation.dataSource }}</small></a></div>
            <div v-if="message.handoff" class="handoff-box"><Headphones :size="18" /><div><b>已自动转入人工队列</b><p>{{ message.handoff.reason }}</p><span class="mono">{{ message.handoff.ticketNo }}</span><span class="badge danger">{{ message.handoff.priority }}</span></div></div>
          </div>
        </article>

        <div v-if="pendingExecutions.length" class="confirmation-stack">
          <div v-for="execution in pendingExecutions" :key="execution.id" class="confirmation-card"><AlertTriangle :size="18" /><div><b>敏感操作等待你的确认</b><p>{{ execution.summary }}</p><small>执行前不会产生业务副作用 · 操作可幂等重放并写入审计</small></div><button class="button warning" @click="confirmTarget = execution">核对并确认</button></div>
        </div>
        <div v-if="completedSensitiveExecutions.length" class="confirmation-stack">
          <div v-for="execution in completedSensitiveExecutions" :key="execution.id" class="confirmation-card completed"><CheckCircle2 :size="18" /><div><b>敏感操作已执行</b><p>{{ execution.summary }}</p><small>执行单 #{{ execution.id }} · 来源 {{ execution.adapterSource }} · 重复确认不会重复生效</small></div><span class="badge success">已完成</span></div>
        </div>

        <div v-if="feedback.visible" class="feedback-panel">
          <template v-if="!feedback.done"><div><b>本次服务是否解决了问题？</b><p>评价提交后，会话与已解决工单进入关闭状态。</p></div><input v-model="feedback.comment" maxlength="512" placeholder="补充评价（可选）" /><div class="rating-buttons"><button v-for="rating in 5" :key="rating" :aria-label="`${rating} 星`" :disabled="feedback.sending" @click="submitFeedback(rating)"><Star :size="20" :fill="feedback.rating >= rating ? 'currentColor' : 'none'" />{{ rating }}</button></div></template>
          <div v-else class="feedback-done"><Check :size="18" />评价已记录，服务闭环完成。</div>
        </div>
      </div>

      <div class="quick-prompts"><button v-for="prompt in quickPrompts" :key="prompt" :disabled="actionsDisabled" @click="send(prompt)">{{ prompt }}<ChevronRight :size="13" /></button></div>
      <footer class="composer">
        <textarea v-model="input" rows="2" maxlength="4000" :disabled="sending || current?.status === 'CLOSED'" :placeholder="current?.status === 'CLOSED' ? '会话已关闭，请新建会话' : isHuman ? '直接发送给人工坐席（Ctrl + Enter）' : '描述问题，Agent 将按需检索知识或调用工具（Ctrl + Enter）'" @keydown.ctrl.enter.prevent="send()" />
        <div class="composer-actions"><span>{{ input.length }} / 4000</span><button v-if="sending" class="button danger" @click="cancelStream"><CircleStop :size="16" />停止</button><button v-else class="button primary" :disabled="!canSend" @click="send()"><Send :size="16" />发送</button></div>
      </footer>
    </section>

    <aside class="catalog-panel panel">
      <header class="panel-header"><div><span class="panel-index">REFERENCE</span><h2>可用业务数据</h2></div><button class="icon-button" aria-label="刷新" @click="loadInitial"><RefreshCw /></button></header>
      <div class="catalog-body">
        <section><div class="section-label">ORDERS</div><button v-for="order in orders" :key="order.orderNo" class="catalog-item" @click="fillOrder(order.orderNo)"><PackageSearch :size="16" /><span><b>{{ order.orderNo }}</b><small>{{ order.product }} · ¥{{ order.amount }}</small></span><em>{{ order.status }}</em></button><div v-if="!orders.length" class="empty-state">当前账号无可见订单</div></section>
        <section><div class="section-label">POLICIES</div><button v-for="policy in policies" :key="policy.policyNo" class="catalog-item" @click="fillPolicy(policy.policyNo)"><ShieldCheck :size="16" /><span><b>{{ policy.policyNo }}</b><small>{{ policy.product }} · {{ policy.maskedHolder }}</small></span><em>{{ policy.status }}</em></button><div v-if="!policies.length" class="empty-state">当前账号无可见保单</div></section>
        <section class="trace-box"><Clock3 :size="16" /><div><b>当前链路可追踪</b><p>SSE 流式响应、工具来源、确认执行、人工消息和最终评价均保留记录。</p></div></section>
      </div>
    </aside>
  </div>

  <div v-if="confirmTarget" class="modal-backdrop" role="presentation" @click.self="confirmTarget = null">
    <section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
      <div class="dialog-icon"><AlertTriangle /></div><span class="panel-index">SENSITIVE ACTION</span><h2 id="confirm-title">确认执行敏感业务操作</h2><p>{{ confirmTarget.summary }}</p>
      <dl><div><dt>工具</dt><dd class="mono">{{ confirmTarget.toolName }}</dd></div><div><dt>数据来源</dt><dd>{{ confirmTarget.adapterSource }}</dd></div><div><dt>幂等执行单</dt><dd class="mono">#{{ confirmTarget.id }}</dd></div></dl>
      <div class="notice warning"><AlertTriangle :size="16" />确认后将调用业务 Adapter；重复确认不会重复产生副作用，结果会写入审计日志。</div>
      <footer><button class="button" :disabled="confirming" @click="confirmTarget = null">取消</button><button class="button warning" :disabled="confirming" @click="confirmExecution">{{ confirming ? '执行中…' : '确认并执行' }}</button></footer>
    </section>
  </div>
</template>

<style scoped>
.chat-layout { height: calc(100vh - 149px); min-height: 560px; display: grid; grid-template-columns: 250px minmax(420px, 1fr) 270px; gap: 12px; }
.conversation-panel, .chat-panel, .catalog-panel { min-height: 0; overflow: hidden; }
.conversation-panel, .catalog-panel, .chat-panel { display: flex; flex-direction: column; }
.conversation-list { overflow: auto; flex: 1; padding: 7px; }
.conversation-row { width: 100%; display: grid; grid-template-columns: 38px 1fr auto; align-items: center; gap: 7px; text-align: left; padding: 9px 8px; color: var(--ink); background: transparent; border: 1px solid transparent; border-bottom-color: var(--line); }
.conversation-row:hover { background: #f2f0e8; }.conversation-row.selected { background: var(--teal-soft); border-color: #9ec9c1; }
.conversation-row.new-row { grid-template-columns: 38px 1fr; color: var(--teal-dark); }
.conversation-index { color: var(--muted); font-size: 10px; }.conversation-copy { min-width: 0; }.conversation-copy b, .conversation-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.conversation-copy b { font-size: 12px; }.conversation-copy small { color: var(--muted); font-size: 10px; margin-top: 3px; }
.conversation-source { display: flex; gap: 8px; padding: 11px 12px; color: #754009; background: var(--amber-soft); border-top: 1px solid #deb17c; font-size: 10px; }.conversation-source span, .conversation-source b { display: block; }.conversation-source b { margin-bottom: 2px; }
.chat-header { min-height: 58px; padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; gap: 10px; border-bottom: 1px solid var(--line); }.chat-title { display: flex; align-items: center; gap: 7px; }.chat-title b { font-size: 14px; }.chat-header p { color: var(--muted); margin: 4px 0 0; font: 10px 'IBM Plex Mono', monospace; }
.live-state { display: flex; gap: 6px; align-items: center; color: var(--muted); font-size: 10px; white-space: nowrap; }.live-state > span { width: 7px; height: 7px; border-radius: 50%; background: var(--amber); }.live-state.live > span { background: var(--teal); box-shadow: 0 0 0 3px var(--teal-soft); }.live-state.offline > span { background: var(--red); }
.chat-alert { display: flex; align-items: center; gap: 7px; padding: 7px 11px; border-bottom: 1px solid; font-size: 11px; }.chat-alert span { flex: 1; }.chat-alert button { color: inherit; background: transparent; font-size: 10px; text-decoration: underline; }.chat-alert.danger { color: #842b2b; background: var(--red-soft); border-color: #d7a19a; }.chat-alert.info { color: #164f4a; background: var(--teal-soft); border-color: #9fc8c1; }
.message-stream { flex: 1; overflow: auto; padding: 15px 16px; scroll-behavior: smooth; }.message-row { display: flex; gap: 9px; max-width: 88%; margin-bottom: 17px; }.message-row.user { flex-direction: row-reverse; margin-left: auto; }.message-avatar { flex: 0 0 auto; width: 30px; height: 30px; display: grid; place-items: center; color: var(--teal-dark); background: var(--teal-soft); border: 1px solid #a2c9c3; }.message-row.user .message-avatar { color: #fff; background: #3e4641; border-color: #3e4641; }.message-row.agent .message-avatar { color: #754009; background: var(--amber-soft); border-color: #deb17c; }
.message-body { min-width: 0; flex: 1; }.message-author { display: flex; align-items: center; gap: 8px; margin: 0 0 4px; font-size: 10px; }.message-row.user .message-author { justify-content: flex-end; }.message-author time, .message-author span { color: var(--muted); }.message-bubble { width: fit-content; max-width: 100%; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.65; padding: 9px 11px; margin: 0; color: #303532; background: #f1efe8; border: 1px solid var(--line); font-size: 13px; }.message-row.user .message-bubble { margin-left: auto; color: #fff; background: #3b433e; border-color: #3b433e; }.message-row.agent .message-bubble { background: #fff7ea; border-color: #d7aa74; }.message-row.system { max-width: 100%; justify-content: center; }.message-row.system .message-avatar, .message-row.system .message-author { display: none; }.message-row.system .message-bubble { color: #555b56; background: transparent; border-style: dashed; font-size: 11px; text-align: center; }
.classification-strip { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 5px; }.classification-strip span { padding: 3px 6px; color: #555b56; background: #edebe4; border: 1px solid var(--line); font-size: 9px; }.classification-strip b { color: var(--ink); }
.tool-step { width: min(540px, 100%); padding: 9px 10px; margin: 0 0 6px; border: 1px solid #aac5c0; background: #f5faf8; }.tool-step > div { display: flex; align-items: center; gap: 6px; }.tool-step > div b { font-size: 11px; margin-right: auto; }.tool-step dl { display: grid; grid-template-columns: auto 1fr; gap: 3px 8px; margin: 7px 0 0; font: 10px 'IBM Plex Mono', monospace; }.tool-step dt { color: var(--muted); }.tool-step dd { margin: 0; overflow-wrap: anywhere; }.tool-step p { color: #3f4541; font-size: 11px; margin: 7px 0 0; padding-top: 6px; border-top: 1px dashed #b8cec9; }.tool-step small { color: var(--muted); font-size: 9px; }
.typing { display: flex; align-items: center; gap: 8px; color: var(--muted); }.typing > span { width: 7px; height: 7px; border-radius: 50%; background: var(--teal); animation: pulse 1s ease-in-out infinite; }.typing-caret { display: inline-block; width: 6px; height: 13px; margin-left: 3px; vertical-align: -2px; background: var(--teal); animation: pulse .8s infinite; }@keyframes pulse { 50% { opacity: .25; } }
.citation-list { width: min(560px, 100%); margin-top: 7px; border: 1px solid var(--line); }.citation-title { display: flex; gap: 6px; align-items: center; padding: 6px 8px; color: var(--muted); background: #eceae4; font-size: 10px; }.citation-list a { display: flex; flex-direction: column; gap: 3px; padding: 8px; text-decoration: none; border-top: 1px solid var(--line); font-size: 10px; }.citation-list a:hover { background: #f3f1e9; }.citation-list a span { color: #555b56; line-height: 1.5; }.citation-list a small { color: var(--teal); }
.handoff-box { width: min(560px, 100%); display: flex; gap: 9px; padding: 10px; margin-top: 7px; color: #743c05; background: var(--amber-soft); border: 1px solid #dba96f; }.handoff-box p { margin: 3px 0 7px; font-size: 11px; }.handoff-box .mono { margin-right: 7px; font-size: 10px; }
.confirmation-stack { display: grid; gap: 7px; margin: 10px 0; }.confirmation-card { display: grid; grid-template-columns: auto 1fr auto; gap: 10px; align-items: center; padding: 11px; color: #713b05; background: var(--amber-soft); border: 1px solid #d8a66e; }.confirmation-card p { margin: 2px 0; font-size: 11px; }.confirmation-card small { color: #8c623a; font-size: 9px; }
.confirmation-card.completed { color: var(--teal-dark); background: var(--teal-soft); border-color: #9bc8c1; }.confirmation-card.completed small { color: #4e625e; }
.feedback-panel { display: grid; grid-template-columns: 1fr minmax(180px, 280px) auto; align-items: center; gap: 10px; padding: 12px; margin-top: 10px; background: var(--teal-soft); border: 1px solid #9bc8c1; }.feedback-panel b { font-size: 12px; }.feedback-panel p { margin: 2px 0 0; color: #4e625e; font-size: 10px; }.feedback-panel input { height: 34px; background: #fff; }.rating-buttons { display: flex; }.rating-buttons button { display: grid; gap: 2px; place-items: center; color: #9b560d; background: transparent; font: 9px 'IBM Plex Mono', monospace; }.rating-buttons button:hover { color: var(--amber); }.feedback-done { grid-column: 1/-1; display: flex; justify-content: center; align-items: center; gap: 7px; color: var(--teal-dark); }
.quick-prompts { display: flex; gap: 5px; padding: 7px 11px; overflow-x: auto; border-top: 1px solid var(--line); }.quick-prompts button { flex: 0 0 auto; display: flex; align-items: center; gap: 4px; padding: 5px 7px; color: #555b56; background: #eeece5; border: 1px solid var(--line); font-size: 9px; }.quick-prompts button:hover { color: var(--teal-dark); border-color: #8fbdb6; }
.composer { padding: 9px 11px 10px; background: #f0eee7; border-top: 1px solid var(--line); }.composer textarea { min-height: 54px; max-height: 130px; background: var(--surface); }.composer-actions { display: flex; align-items: center; justify-content: flex-end; gap: 7px; margin-top: 6px; }.composer-actions > span { color: var(--muted); margin-right: auto; font: 9px 'IBM Plex Mono', monospace; }
.catalog-body { flex: 1; overflow: auto; padding: 12px; }.catalog-body section + section { margin-top: 18px; }.catalog-item { width: 100%; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 8px; padding: 9px 7px; text-align: left; color: var(--ink); background: transparent; border-bottom: 1px solid var(--line); }.catalog-item:hover { background: var(--teal-soft); }.catalog-item span, .catalog-item b, .catalog-item small { display: block; min-width: 0; }.catalog-item b { font: 600 11px 'IBM Plex Mono', monospace; }.catalog-item small { color: var(--muted); margin-top: 2px; font-size: 9px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.catalog-item em { color: var(--teal); font-size: 9px; font-style: normal; }.trace-box { display: flex; gap: 8px; padding: 10px; background: #edebe4; border: 1px solid var(--line); }.trace-box p { color: var(--muted); margin: 3px 0 0; font-size: 10px; line-height: 1.5; }
.modal-backdrop { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 18px; background: rgba(19,23,20,.62); }.confirm-dialog { width: min(480px, 100%); padding: 24px; background: var(--surface); border: 1px solid var(--line-strong); box-shadow: 0 24px 60px rgba(0,0,0,.28); }.dialog-icon { width: 43px; height: 43px; display: grid; place-items: center; color: #834000; background: var(--amber-soft); border: 1px solid #d8a66e; margin-bottom: 13px; }.confirm-dialog h2 { font-size: 20px; margin: 6px 0; }.confirm-dialog > p { color: #555b56; line-height: 1.6; }.confirm-dialog dl { margin: 14px 0; border: 1px solid var(--line); }.confirm-dialog dl div { display: grid; grid-template-columns: 110px 1fr; padding: 7px 9px; border-bottom: 1px solid var(--line); font-size: 11px; }.confirm-dialog dl div:last-child { border-bottom: 0; }.confirm-dialog dt { color: var(--muted); }.confirm-dialog dd { margin: 0; overflow-wrap: anywhere; }.confirm-dialog footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
@media (max-width: 1250px) { .chat-layout { grid-template-columns: 220px minmax(420px, 1fr); }.catalog-panel { display: none; } }
@media (max-width: 820px) { .chat-layout { height: calc(100vh - 139px); min-height: 520px; grid-template-columns: 1fr; }.conversation-panel { display: none; }.message-row { max-width: 96%; }.feedback-panel { grid-template-columns: 1fr; }.rating-buttons { justify-content: flex-start; } }
@media (max-width: 520px) { .chat-layout { height: calc(100dvh - 124px); min-height: 430px; margin: 0 -2px; }.chat-header { min-height: 51px; padding: 8px 10px; }.chat-header p { display: none; }.message-stream { padding: 11px 9px; }.message-row { max-width: 100%; }.message-avatar { width: 27px; height: 27px; }.quick-prompts { padding: 5px 8px; }.composer { padding: 7px 8px; }.confirmation-card { grid-template-columns: auto 1fr; }.confirmation-card .button { grid-column: 1/-1; }.modal-backdrop { padding: 0; align-items: end; }.confirm-dialog { padding: 20px; } }
</style>
