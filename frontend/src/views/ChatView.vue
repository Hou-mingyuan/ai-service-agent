<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api, chatStream } from '../api'
import DemoWizard from '../components/DemoWizard.vue'

interface ToolStep {
  name: string
  arguments: Record<string, any>
  pending: boolean
  ok?: boolean
  summary?: string
}
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  meta?: { intentLabel: string; sentimentLabel: string; score: number }
  tools: ToolStep[]
  handoff?: { ticketNo: string; priority: string; reason: string }
  streaming: boolean
}

const messages = ref<ChatMessage[]>([])
const input = ref('')
const streaming = ref(false)
const conversationId = ref<number | null>(null)
const userName = ref('访客')
const scroller = ref<HTMLElement | null>(null)
const llmProvider = ref('mock')

const feedback = reactive({ show: false, done: false, rating: 0 })
const orders = ref<any[]>([])
const policies = ref<any[]>([])

const suggestions = [
  '帮我查订单123',
  '订单123的快递到哪了',
  '查一下保单 PAI2024001',
  '我要把订单1003改期到2026-07-15',
  '你们太差了，我要投诉！'
]

const toolLabels: Record<string, string> = {
  query_order: '查询订单',
  query_logistics: '查询物流',
  query_policy: '查询保单',
  create_ticket: '创建工单',
  query_ticket: '查询工单',
  reschedule_appointment: '预约改期'
}

const isMock = computed(() => llmProvider.value === 'mock')
const toolCallCount = computed(() =>
  messages.value.reduce((n, m) => n + m.tools.filter((t) => !t.pending).length, 0)
)

onMounted(async () => {
  greet()
  try {
    const health = await api.get<{ status: string; llmProvider: string }>('/api/health')
    llmProvider.value = health.llmProvider
  } catch {
    /* ignore */
  }
  try {
    orders.value = await api.get<any[]>('/api/catalog/orders')
    policies.value = await api.get<any[]>('/api/catalog/policies')
  } catch {
    /* ignore */
  }
})

function greet() {
  messages.value.push({
    role: 'assistant',
    content:
      '您好，我是智能客服「智答」👋 可以帮您查询订单、物流、保单，办理预约改期，或登记工单转人工。右侧示例数据或下方快捷问题均可一键发送；左侧 Mock 演示向导可带您走完完整闭环。',
    tools: [],
    streaming: false
  })
}

async function scrollDown() {
  await nextTick()
  scroller.value?.scrollTo({ top: scroller.value.scrollHeight, behavior: 'smooth' })
}

function pretty(args: Record<string, any>) {
  const clone = { ...args }
  delete clone.conversation_id
  delete clone.customer
  return Object.entries(clone)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${v}`)
    .join(', ')
}

async function send(text?: string) {
  const msg = (text ?? input.value).trim()
  if (!msg || streaming.value) return
  input.value = ''
  feedback.show = false
  feedback.done = false
  messages.value.push({ role: 'user', content: msg, tools: [], streaming: false })

  const assistant: ChatMessage = { role: 'assistant', content: '', tools: [], streaming: true }
  messages.value.push(assistant)
  streaming.value = true
  scrollDown()

  try {
    await chatStream(
      { conversationId: conversationId.value, message: msg, userName: userName.value },
      (event, data) => {
        if (event === 'start') {
          conversationId.value = data.conversationId
        } else if (event === 'meta') {
          assistant.meta = {
            intentLabel: data.intentLabel,
            sentimentLabel: data.sentimentLabel,
            score: data.score
          }
        } else if (event === 'tool_call') {
          assistant.tools.push({ name: data.name, arguments: data.arguments || {}, pending: true })
        } else if (event === 'tool_result') {
          const t = [...assistant.tools].reverse().find((x) => x.name === data.name && x.pending)
          if (t) {
            t.pending = false
            t.ok = data.ok
            t.summary = data.summary
          }
        } else if (event === 'token') {
          assistant.content += data.text
          scrollDown()
        } else if (event === 'handoff') {
          assistant.handoff = data
        } else if (event === 'done') {
          assistant.streaming = false
          if (conversationId.value) feedback.show = true
        } else if (event === 'error') {
          assistant.content += `\n⚠️ ${data.message}`
          assistant.streaming = false
        }
      }
    )
  } catch (e: any) {
    assistant.content += `\n⚠️ 连接失败：${e.message}`
  } finally {
    assistant.streaming = false
    streaming.value = false
    scrollDown()
  }
}

async function submitFeedback(rating: number) {
  feedback.rating = rating
  try {
    await api.post('/api/feedback', { conversationId: conversationId.value, rating })
    feedback.done = true
  } catch {
    feedback.done = true
  }
}

function newSession() {
  messages.value = []
  conversationId.value = null
  feedback.show = false
  feedback.done = false
  greet()
}

function sentimentClass(label?: string) {
  if (label === '负面') return 'badge-red'
  if (label === '正面') return 'badge-green'
  return 'badge-gray'
}

function copyOrderNo(no: string) {
  input.value = `帮我查订单${no}`
}
</script>

<template>
  <div class="chat-page">
    <DemoWizard class="wizard-panel" @send-prompt="send" />

    <section class="chat card">
      <header class="chat-head">
        <div class="head-left">
          <div class="head-title">
            <b>智能对话</b>
            <span v-if="isMock" class="badge badge-purple mock-tag">Mock 零密钥</span>
          </div>
          <div class="head-meta">
            <span v-if="conversationId" class="conv-id">会话 #{{ conversationId }}</span>
            <span class="stat-pill">工具调用 {{ toolCallCount }}</span>
            <span class="stat-pill muted">模型 {{ llmProvider }}</span>
          </div>
        </div>
        <button class="btn-outline" @click="newSession">＋ 新会话</button>
      </header>

      <div ref="scroller" class="messages">
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="avatar" :class="m.role">{{ m.role === 'user' ? '我' : '智' }}</div>
          <div class="bubble-col">
            <div v-if="m.meta" class="meta-row">
              <span class="badge badge-purple">意图 {{ m.meta.intentLabel }}</span>
              <span class="badge" :class="sentimentClass(m.meta.sentimentLabel)">
                情绪 {{ m.meta.sentimentLabel }}
              </span>
            </div>

            <div v-for="(t, ti) in m.tools" :key="ti" class="tool-card">
              <div class="tool-top">
                <span class="tool-icon">{{ t.pending ? '⏳' : t.ok ? '✓' : '!' }}</span>
                <div class="tool-main">
                  <div class="tool-name">{{ toolLabels[t.name] || t.name }}</div>
                  <div v-if="pretty(t.arguments)" class="tool-args">{{ pretty(t.arguments) }}</div>
                </div>
                <span class="badge" :class="t.pending ? 'badge-blue' : t.ok ? 'badge-green' : 'badge-amber'">
                  {{ t.pending ? '调用中' : t.ok ? '成功' : '无结果' }}
                </span>
              </div>
              <div v-if="t.summary" class="tool-result">{{ t.summary }}</div>
            </div>

            <div v-if="m.content" class="bubble" :class="m.role">
              {{ m.content }}<span v-if="m.streaming" class="cursor">▋</span>
            </div>
            <div v-else-if="m.streaming && m.tools.length === 0" class="bubble assistant typing">
              思考中<span class="cursor">▋</span>
            </div>

            <div v-if="m.handoff" class="handoff-card">
              <div class="handoff-top">🚨 已转人工</div>
              <div class="handoff-grid">
                <span>工单 <b>{{ m.handoff.ticketNo }}</b></span>
                <span class="badge badge-red">{{ m.handoff.priority }}</span>
              </div>
              <div class="handoff-reason">{{ m.handoff.reason }}</div>
            </div>
          </div>
        </div>

        <div v-if="feedback.show" class="feedback-bar">
          <template v-if="!feedback.done">
            <span>本次服务满意吗？</span>
            <div class="stars">
              <span v-for="s in 5" :key="s" class="star" @click="submitFeedback(s)">★</span>
            </div>
          </template>
          <template v-else>
            <span>感谢评价 🙏（{{ feedback.rating }} 星）</span>
          </template>
        </div>
      </div>

      <div class="quick-bar">
        <span class="quick-label">快捷</span>
        <button
          v-for="s in suggestions"
          :key="s"
          class="chip"
          :disabled="streaming"
          @click="send(s)"
        >
          {{ s }}
        </button>
      </div>

      <footer class="composer">
        <input
          v-model="input"
          placeholder="输入问题，Enter 发送…"
          @keyup.enter="send()"
          :disabled="streaming"
        />
        <button class="btn-primary send-btn" :disabled="streaming || !input.trim()" @click="send()">
          {{ streaming ? '…' : '发送' }}
        </button>
      </footer>
    </section>

    <aside class="catalog card">
      <div class="cat-head">📚 示例业务数据</div>
      <div class="cat-section">
        <div class="cat-label">订单 · 点击填入</div>
        <button
          v-for="o in orders"
          :key="o.orderNo"
          class="cat-item"
          @click="copyOrderNo(o.orderNo)"
        >
          <div class="cat-row">
            <b>#{{ o.orderNo }}</b>
            <span>{{ o.status }}</span>
          </div>
          <div class="cat-sub">{{ o.product }} · ¥{{ o.amount }}</div>
        </button>
      </div>
      <div class="cat-section">
        <div class="cat-label">保单</div>
        <div v-for="p in policies" :key="p.policyNo" class="cat-item static">
          <div class="cat-row">
            <b>{{ p.policyNo }}</b>
            <span>{{ p.status }}</span>
          </div>
          <div class="cat-sub">{{ p.product }} · {{ p.holder }}</div>
        </div>
      </div>
      <p class="cat-tip">SSE 流式 + 工具可视化 · 投诉话术将自动建单并推送坐席</p>
    </aside>
  </div>
</template>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 260px 1fr 280px;
  gap: 16px;
  height: 100%;
  min-height: 0;
}
.wizard-panel { min-height: 0; }
.chat { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }

.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  gap: 12px;
}
.head-title { display: flex; align-items: center; gap: 8px; }
.mock-tag { font-size: 11px; }
.head-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.conv-id, .stat-pill { font-size: 12px; color: var(--muted); }
.stat-pill {
  background: #f8fafc;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--border);
}
.stat-pill.muted { color: #94a3b8; }

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: linear-gradient(180deg, #fafbff 0%, #fff 120px);
}

.msg-row { display: flex; gap: 10px; max-width: 92%; }
.msg-row.user { align-self: flex-end; flex-direction: row-reverse; }
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 13px;
  color: #fff;
}
.avatar.assistant { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.avatar.user { background: #0ea5e9; }
.bubble-col { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.msg-row.user .bubble-col { align-items: flex-end; }

.bubble {
  padding: 11px 14px;
  border-radius: 16px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  max-width: 100%;
}
.bubble.assistant {
  background: #fff;
  border: 1px solid var(--border);
  border-top-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.bubble.user {
  background: linear-gradient(135deg, #4f46e5, #4338ca);
  color: #fff;
  border-top-right-radius: 4px;
}
.typing { color: var(--muted); }

.meta-row { display: flex; gap: 6px; flex-wrap: wrap; }

.tool-card {
  background: #fff;
  border: 1px solid #e0e7ff;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  width: 100%;
}
.tool-top { display: flex; align-items: flex-start; gap: 10px; }
.tool-icon { font-size: 16px; line-height: 1; margin-top: 2px; }
.tool-main { flex: 1; min-width: 0; }
.tool-name { font-weight: 600; color: var(--primary-600); }
.tool-args {
  color: var(--muted);
  font-family: ui-monospace, monospace;
  font-size: 11px;
  margin-top: 2px;
  word-break: break-all;
}
.tool-result {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--border);
  color: #334155;
  font-size: 12px;
}

.handoff-card {
  background: linear-gradient(135deg, #fff7ed, #ffedd5);
  border: 1px solid #fed7aa;
  border-radius: 12px;
  padding: 12px 14px;
  font-size: 13px;
  width: 100%;
}
.handoff-top { font-weight: 700; color: #c2410c; margin-bottom: 6px; }
.handoff-grid { display: flex; align-items: center; gap: 8px; }
.handoff-reason { color: var(--muted); margin-top: 6px; font-size: 12px; }

.cursor { animation: blink 1s steps(2) infinite; color: var(--primary); }
@keyframes blink { 50% { opacity: 0; } }

.feedback-bar {
  align-self: center;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--primary-soft);
  padding: 10px 18px;
  border-radius: 999px;
  color: var(--primary-600);
}
.stars { display: flex; gap: 2px; }
.star { cursor: pointer; color: #f59e0b; font-size: 20px; transition: transform 0.1s; }
.star:hover { transform: scale(1.15); }

.quick-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 18px 0;
  border-top: 1px solid var(--border);
}
.quick-label { font-size: 12px; color: var(--muted); flex-shrink: 0; }
.chip {
  background: var(--primary-soft);
  color: var(--primary-600);
  font-size: 12px;
  border-radius: 999px;
  padding: 5px 11px;
}
.chip:hover:not(:disabled) { background: #e0e7ff; }

.composer {
  display: flex;
  gap: 10px;
  padding: 12px 18px 16px;
}
.composer input { flex: 1; }
.send-btn { min-width: 72px; }

.catalog { padding: 0; overflow-y: auto; display: flex; flex-direction: column; }
.cat-head {
  font-weight: 700;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: #fafbff;
}
.cat-section { padding: 12px 14px 0; }
.cat-label { font-size: 12px; color: var(--muted); margin-bottom: 8px; font-weight: 600; }
.cat-item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid transparent;
  margin-bottom: 8px;
}
.cat-item:not(.static):hover {
  border-color: var(--primary);
  background: var(--primary-soft);
}
.cat-row { display: flex; justify-content: space-between; gap: 8px; font-size: 13px; }
.cat-row span { color: var(--muted); font-size: 12px; }
.cat-sub { color: var(--muted); font-size: 12px; margin-top: 2px; }
.cat-tip {
  margin: auto 14px 14px;
  font-size: 11px;
  color: var(--muted);
  line-height: 1.5;
  padding-top: 8px;
  border-top: 1px dashed var(--border);
}

@media (max-width: 1100px) {
  .chat-page { grid-template-columns: 1fr 260px; }
  .wizard-panel { display: none; }
}
@media (max-width: 800px) {
  .chat-page { grid-template-columns: 1fr; }
  .catalog { display: none; }
}
</style>
