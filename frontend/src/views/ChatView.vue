<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { api, chatStream } from '../api'

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

onMounted(async () => {
  greet()
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
    content: '您好，我是智能客服「智答」👋 可以帮您查询订单、物流、保单，办理预约改期，或登记工单转人工。试试右侧的示例数据或下方的快捷问题吧～',
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
    await chatStream({ conversationId: conversationId.value, message: msg, userName: userName.value },
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
      })
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
</script>

<template>
  <div class="chat-layout">
    <section class="chat card">
      <div class="chat-head">
        <div>
          <b>对话窗口</b>
          <span class="conv-id" v-if="conversationId">会话 #{{ conversationId }}</span>
        </div>
        <button class="btn-outline" @click="newSession">＋ 新会话</button>
      </div>

      <div ref="scroller" class="messages">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="avatar" :class="m.role">{{ m.role === 'user' ? '我' : '智' }}</div>
          <div class="bubble-wrap">
            <div v-if="m.meta" class="meta-row">
              <span class="badge badge-purple">意图：{{ m.meta.intentLabel }}</span>
              <span class="badge" :class="sentimentClass(m.meta.sentimentLabel)">
                情绪：{{ m.meta.sentimentLabel }}
              </span>
            </div>

            <div v-for="(t, ti) in m.tools" :key="ti" class="tool">
              <div class="tool-head">
                <span class="badge badge-blue">🔧 {{ toolLabels[t.name] || t.name }}</span>
                <span class="tool-args">{{ pretty(t.arguments) }}</span>
                <span v-if="t.pending" class="dots">调用中…</span>
                <span v-else class="badge" :class="t.ok ? 'badge-green' : 'badge-amber'">
                  {{ t.ok ? '成功' : '无结果' }}
                </span>
              </div>
              <div v-if="t.summary" class="tool-summary">{{ t.summary }}</div>
            </div>

            <div v-if="m.content" class="bubble" :class="m.role">
              {{ m.content }}<span v-if="m.streaming" class="cursor">▋</span>
            </div>
            <div v-else-if="m.streaming && m.tools.length === 0" class="bubble assistant typing">
              思考中<span class="cursor">▋</span>
            </div>

            <div v-if="m.handoff" class="handoff">
              🚨 已转人工 · 工单 <b>{{ m.handoff.ticketNo }}</b>
              <span class="badge badge-red">{{ m.handoff.priority }}</span>
              <div class="handoff-reason">{{ m.handoff.reason }}</div>
            </div>
          </div>
        </div>

        <div v-if="feedback.show" class="feedback">
          <template v-if="!feedback.done">
            <span>本次服务您还满意吗？</span>
            <div class="stars">
              <span v-for="s in 5" :key="s" class="star" @click="submitFeedback(s)">★</span>
            </div>
          </template>
          <template v-else>
            <span>感谢您的评价 🙏（{{ feedback.rating }} 星）</span>
          </template>
        </div>
      </div>

      <div class="chips">
        <button v-for="s in suggestions" :key="s" class="chip" :disabled="streaming" @click="send(s)">
          {{ s }}
        </button>
      </div>
      <div class="composer">
        <input
          v-model="input"
          placeholder="输入您的问题，回车发送…"
          @keyup.enter="send()"
          :disabled="streaming"
        />
        <button class="btn-primary" :disabled="streaming || !input.trim()" @click="send()">
          {{ streaming ? '回复中…' : '发送' }}
        </button>
      </div>
    </section>

    <aside class="side card">
      <div class="side-title">📦 示例订单</div>
      <div v-for="o in orders" :key="o.orderNo" class="side-item">
        <div class="side-line">
          <b>#{{ o.orderNo }}</b><span>{{ o.product }}</span>
        </div>
        <div class="side-sub">{{ o.customer }} · ¥{{ o.amount }} · {{ o.status }}</div>
      </div>
      <div class="side-title">🛡️ 示例保单</div>
      <div v-for="p in policies" :key="p.policyNo" class="side-item">
        <div class="side-line">
          <b>{{ p.policyNo }}</b><span>{{ p.product }}</span>
        </div>
        <div class="side-sub">{{ p.holder }} · {{ p.status }}</div>
      </div>
      <div class="side-tip">提示：可直接对我说「查订单 1002」「PAI2024002 的保单」等。</div>
    </aside>
  </div>
</template>

<style scoped>
.chat-layout { display: grid; grid-template-columns: 1fr 300px; gap: 16px; height: 100%; }
.chat { display: flex; flex-direction: column; overflow: hidden; }
.chat-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px; border-bottom: 1px solid var(--border);
}
.conv-id { color: var(--muted); font-size: 12px; margin-left: 10px; }
.messages { flex: 1; overflow-y: auto; padding: 18px; display: flex; flex-direction: column; gap: 16px; }

.msg { display: flex; gap: 10px; max-width: 88%; }
.msg.user { align-self: flex-end; flex-direction: row-reverse; }
.avatar {
  width: 34px; height: 34px; border-radius: 10px; flex-shrink: 0;
  display: grid; place-items: center; font-weight: 700; font-size: 13px; color: #fff;
}
.avatar.assistant { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.avatar.user { background: #0ea5e9; }
.bubble-wrap { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.msg.user .bubble-wrap { align-items: flex-end; }

.bubble {
  padding: 10px 14px; border-radius: 14px; white-space: pre-wrap; word-break: break-word;
  font-size: 14px;
}
.bubble.assistant { background: #fff; border: 1px solid var(--border); border-top-left-radius: 4px; }
.bubble.user { background: var(--primary); color: #fff; border-top-right-radius: 4px; }
.typing { color: var(--muted); }

.meta-row { display: flex; gap: 6px; flex-wrap: wrap; }
.tool {
  background: #f8fafc; border: 1px solid var(--border); border-radius: 12px;
  padding: 8px 12px; font-size: 13px;
}
.tool-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.tool-args { color: var(--muted); font-family: ui-monospace, monospace; font-size: 12px; }
.tool-summary { margin-top: 6px; color: #334155; }
.dots { color: var(--primary); font-size: 12px; }

.handoff {
  background: #fff7ed; border: 1px solid #fed7aa; border-radius: 12px;
  padding: 10px 14px; font-size: 13px; color: #9a3412;
}
.handoff-reason { color: var(--muted); margin-top: 4px; font-size: 12px; }

.cursor { animation: blink 1s steps(2) infinite; color: var(--primary); }
@keyframes blink { 50% { opacity: 0; } }

.feedback {
  align-self: center; display: flex; align-items: center; gap: 12px;
  background: var(--primary-soft); padding: 10px 18px; border-radius: 999px; color: var(--primary-600);
}
.stars { display: flex; gap: 2px; }
.star { cursor: pointer; color: #f59e0b; font-size: 20px; transition: transform 0.1s; }
.star:hover { transform: scale(1.2); }

.chips { display: flex; gap: 8px; flex-wrap: wrap; padding: 10px 18px 0; }
.chip {
  background: var(--primary-soft); color: var(--primary-600); font-size: 13px;
  border-radius: 999px; padding: 6px 12px;
}
.chip:hover:not(:disabled) { background: #e0e7ff; }
.composer { display: flex; gap: 10px; padding: 12px 18px 16px; }
.composer input { flex: 1; }

.side { padding: 16px; overflow-y: auto; }
.side-title { font-weight: 700; margin: 6px 0 10px; }
.side-item { padding: 8px 10px; border-radius: 10px; background: #f8fafc; margin-bottom: 8px; }
.side-line { display: flex; justify-content: space-between; gap: 8px; }
.side-line span { color: var(--muted); font-size: 13px; }
.side-sub { color: var(--muted); font-size: 12px; margin-top: 2px; }
.side-tip { color: var(--muted); font-size: 12px; margin-top: 12px; line-height: 1.5; }

@media (max-width: 900px) {
  .chat-layout { grid-template-columns: 1fr; }
  .side { display: none; }
}
</style>
