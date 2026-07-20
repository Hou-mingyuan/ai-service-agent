<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import {
  AlertTriangle, BarChart3, CalendarDays, CheckCircle2, Clock3, Headphones,
  MessageSquare, RefreshCw, ShieldAlert, Star, TicketCheck
} from '@lucide/vue'
import { api } from '../api'
import type { DashboardData } from '../types'
import { categoryLabels, errorMessage, formatDuration, priorityLabels, statusLabels } from '../ui'

interface Kpi { key: string; label: string; value: string | number; hint: string; icon: Component; tone: string }

const data = ref<DashboardData | null>(null)
const loading = ref(true)
const error = ref('')
const from = ref(dateOffset(-6))
const to = ref(dateOffset(0))

const kpis = computed<Kpi[]>(() => data.value ? [
  { key: 'conversations', label: '新建会话', value: data.value.kpis.conversations, hint: `${data.value.counts.messages} 条消息`, icon: MessageSquare, tone: 'teal' },
  { key: 'avgFirstResponseSeconds', label: '平均首次响应', value: formatDuration(data.value.kpis.avgFirstResponseSeconds), hint: '仅统计已有回复', icon: Clock3, tone: 'blue' },
  { key: 'resolutionRate', label: '会话解决率', value: `${data.value.kpis.resolutionRate}%`, hint: '范围内已关闭会话', icon: CheckCircle2, tone: 'teal' },
  { key: 'handoffRate', label: '转人工率', value: `${data.value.kpis.handoffRate}%`, hint: '进入人工队列会话', icon: Headphones, tone: 'amber' },
  { key: 'openTickets', label: '未结工单', value: data.value.kpis.openTickets, hint: `${data.value.counts.tickets} 条范围内工单`, icon: TicketCheck, tone: 'blue' },
  { key: 'ticketResolutionRate', label: '工单解决率', value: `${data.value.kpis.ticketResolutionRate}%`, hint: '已解决或已关闭', icon: BarChart3, tone: 'teal' },
  { key: 'slaBreaches', label: 'SLA 超时', value: data.value.kpis.slaBreaches, hint: '已记录超时工单', icon: ShieldAlert, tone: data.value.kpis.slaBreaches ? 'red' : 'teal' },
  { key: 'satisfaction', label: '客户满意度', value: data.value.counts.feedback ? `${data.value.kpis.satisfaction} / 5` : '暂无评价', hint: `${data.value.counts.feedback} 份有效评价`, icon: Star, tone: 'amber' }
] : [])

const chartMax = computed(() => Math.max(1, ...(data.value?.trend.flatMap((row) => [row.conversations, row.handoffs, row.closedTickets]) || [1])))
const conversationPoints = computed(() => points('conversations'))
const handoffPoints = computed(() => points('handoffs'))
const closedPoints = computed(() => points('closedTickets'))
const statusBars = computed(() => bars(data.value?.tickets.byStatus || {}, statusLabels))
const categoryBars = computed(() => bars(data.value?.tickets.byCategory || {}, categoryLabels))
const priorityBars = computed(() => bars(data.value?.tickets.byPriority || {}, priorityLabels))

onMounted(load)

async function load() {
  if (!from.value || !to.value || from.value > to.value) { error.value = '开始日期不能晚于结束日期'; return }
  const days = (new Date(to.value).getTime() - new Date(from.value).getTime()) / 86400000 + 1
  if (days > 90) { error.value = '日期范围最多为 90 天'; return }
  loading.value = true
  error.value = ''
  try {
    data.value = await api.get<DashboardData>(`/api/dashboard/overview?from=${from.value}&to=${to.value}`, { dedupe: false })
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function points(field: 'conversations' | 'handoffs' | 'closedTickets') {
  const rows = data.value?.trend || []
  if (!rows.length) return ''
  return rows.map((row, index) => {
    const x = rows.length === 1 ? 50 : 4 + index * (92 / (rows.length - 1))
    const y = 90 - (row[field] / chartMax.value) * 75
    return `${x},${y}`
  }).join(' ')
}

function bars(values: Record<string, number>, labels: Record<string, string>) {
  const entries = Object.entries(values).map(([key, value]) => ({ key, label: labels[key] || key, value }))
  const max = Math.max(1, ...entries.map((item) => item.value))
  return entries.map((item) => ({ ...item, width: item.value / max * 100 }))
}

function dateOffset(offset: number) {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>

<template>
  <div class="dashboard-page">
    <section class="dashboard-controls panel"><div><span class="panel-index">PERFORMANCE WINDOW</span><h2>服务运营总览</h2><p v-if="data">时区 {{ data.range.timezone }} · 模型 {{ data.llmProvider }} · 指标按会话/工单创建时间归属</p></div><form @submit.prevent="load"><label><span>开始日期</span><input v-model="from" type="date" /></label><span class="date-separator">—</span><label><span>结束日期</span><input v-model="to" type="date" /></label><button class="button primary" :disabled="loading"><RefreshCw :size="15" />{{ loading ? '计算中…' : '重新计算' }}</button></form></section>
    <div v-if="loading" class="loading-line" />
    <div v-if="error" class="dashboard-alert"><AlertTriangle :size="16" />{{ error }}<button @click="error = ''">关闭</button></div>

    <section v-if="data" class="kpi-grid"><article v-for="kpi in kpis" :key="kpi.key" class="kpi-card panel" :class="kpi.tone"><span class="kpi-icon"><component :is="kpi.icon" /></span><div><small>{{ kpi.label }}</small><b>{{ kpi.value }}</b><em>{{ kpi.hint }}</em></div><span class="kpi-index mono">{{ kpi.key }}</span></article></section>

    <div v-if="data" class="dashboard-grid">
      <section class="trend-panel panel"><header class="panel-header"><div><span class="panel-index">DAILY TREND</span><h2>每日服务趋势</h2><p>会话、转人工与关闭工单按天对齐。</p></div><div class="chart-legend"><span class="conversations">会话</span><span class="handoffs">转人工</span><span class="closed">关闭工单</span></div></header><div class="trend-chart" role="img" aria-label="每日会话、转人工和关闭工单趋势折线图"><svg viewBox="0 0 100 100" preserveAspectRatio="none"><line v-for="y in [15,40,65,90]" :key="y" x1="4" x2="96" :y1="y" :y2="y" class="grid-line" /><polyline :points="conversationPoints" class="series conversations" /><polyline :points="handoffPoints" class="series handoffs" /><polyline :points="closedPoints" class="series closed" /></svg><div class="x-labels"><span v-for="(row, index) in data.trend" :key="row.date" v-show="data.trend.length <= 14 || index % Math.ceil(data.trend.length / 7) === 0 || index === data.trend.length - 1">{{ row.date.slice(5) }}</span></div></div><div v-if="!data.trend.length" class="empty-state">日期范围内没有趋势数据</div><div class="trend-table-wrap"><table><thead><tr><th>日期</th><th>会话</th><th>转人工</th><th>关闭工单</th><th>SLA 超时</th></tr></thead><tbody><tr v-for="row in data.trend" :key="row.date"><td class="mono">{{ row.date }}</td><td>{{ row.conversations }}</td><td>{{ row.handoffs }}</td><td>{{ row.closedTickets }}</td><td :class="{ breach: row.slaBreaches }">{{ row.slaBreaches }}</td></tr></tbody></table></div></section>

      <section class="distribution-panel panel"><header class="panel-header"><div><span class="panel-index">DISTRIBUTION</span><h2>工单结构</h2><p>同一统计周期内新建工单分布。</p></div></header><div class="distribution-groups"><div><span class="section-label">BY STATUS</span><div v-if="statusBars.length" class="bar-list"><div v-for="bar in statusBars" :key="bar.key" class="bar-row"><span>{{ bar.label }}</span><div><i :style="{ width: `${bar.width}%` }" /></div><b>{{ bar.value }}</b></div></div><p v-else class="mini-empty">暂无状态数据</p></div><div><span class="section-label">BY CATEGORY</span><div v-if="categoryBars.length" class="bar-list"><div v-for="bar in categoryBars" :key="bar.key" class="bar-row"><span>{{ bar.label }}</span><div><i :style="{ width: `${bar.width}%` }" /></div><b>{{ bar.value }}</b></div></div><p v-else class="mini-empty">暂无类别数据</p></div><div><span class="section-label">BY PRIORITY</span><div v-if="priorityBars.length" class="bar-list"><div v-for="bar in priorityBars" :key="bar.key" class="bar-row"><span>{{ bar.label }}</span><div><i :style="{ width: `${bar.width}%` }" /></div><b>{{ bar.value }}</b></div></div><p v-else class="mini-empty">暂无优先级数据</p></div></div></section>

      <section class="definition-panel panel"><header class="panel-header"><div><span class="panel-index">METRIC DICTIONARY</span><h2>指标口径</h2><p>避免把不同分母或时间窗口混为同一指标。</p></div><CalendarDays :size="20" /></header><dl><div v-for="(definition, key) in data.definitions" :key="key"><dt class="mono">{{ key }}</dt><dd>{{ definition }}</dd></div></dl><footer><span>辅助计数</span><b>{{ data.counts.toolCalls }} 次工具调用</b><b>{{ data.counts.feedback }} 份评价</b><b>{{ data.counts.messages }} 条消息</b></footer></section>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page { display: grid; gap: 12px; }.dashboard-controls { min-height: 78px; display: flex; justify-content: space-between; align-items: center; gap: 18px; padding: 13px 16px; }.dashboard-controls h2 { margin: 3px 0; font-size: 18px; }.dashboard-controls p { margin: 0; color: var(--muted); font-size: 10px; }.dashboard-controls form { display: flex; align-items: flex-end; gap: 7px; }.dashboard-controls label span { display: block; color: var(--muted); margin-bottom: 4px; font-size: 9px; }.dashboard-controls input { width: 135px; height: 36px; font-size: 10px; }.date-separator { padding-bottom: 9px; color: var(--muted); }.dashboard-alert { display: flex; align-items: center; gap: 7px; padding: 8px 11px; color: #842b2b; background: var(--red-soft); border: 1px solid #d7a19a; font-size: 10px; }.dashboard-alert button { margin-left: auto; color: inherit; background: transparent; text-decoration: underline; }
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }.kpi-card { min-width: 0; display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 10px; padding: 12px; position: relative; overflow: hidden; }.kpi-icon { width: 39px; height: 39px; display: grid; place-items: center; color: var(--teal); background: var(--teal-soft); border: 1px solid #9fc8c1; }.kpi-icon svg { width: 18px; }.kpi-card small, .kpi-card b, .kpi-card em { display: block; }.kpi-card small { color: var(--muted); font-size: 9px; }.kpi-card b { margin: 1px 0; font-size: 19px; line-height: 1.25; white-space: nowrap; }.kpi-card em { color: var(--muted); font-size: 8px; font-style: normal; }.kpi-index { position: absolute; right: 6px; bottom: 4px; max-width: 45%; color: rgba(30,35,32,.13); font-size: 8px; overflow: hidden; white-space: nowrap; }.kpi-card.blue .kpi-icon { color: var(--blue); background: var(--blue-soft); border-color: #a8c7dc; }.kpi-card.amber .kpi-icon { color: var(--amber); background: var(--amber-soft); border-color: #dcb07b; }.kpi-card.red .kpi-icon { color: var(--red); background: var(--red-soft); border-color: #d5a19b; }
.dashboard-grid { display: grid; grid-template-columns: minmax(520px, 1.45fr) minmax(310px, .8fr); gap: 12px; align-items: start; }.trend-panel { min-width: 0; overflow: hidden; }.chart-legend { display: flex; gap: 12px; font-size: 9px; }.chart-legend span::before { content: ''; display: inline-block; width: 13px; height: 2px; margin-right: 4px; vertical-align: middle; background: var(--teal); }.chart-legend .handoffs::before { background: var(--amber); }.chart-legend .closed::before { background: var(--blue); }.trend-chart { height: 260px; padding: 12px 14px 0; }.trend-chart svg { width: 100%; height: 220px; overflow: visible; }.grid-line { stroke: #ddd9d0; stroke-width: .35; stroke-dasharray: 1.5 1.5; }.series { fill: none; stroke: var(--teal); stroke-width: 1.2; vector-effect: non-scaling-stroke; }.series.handoffs { stroke: var(--amber); }.series.closed { stroke: var(--blue); }.x-labels { display: flex; justify-content: space-between; color: var(--muted); font: 8px 'IBM Plex Mono', monospace; }.trend-table-wrap { max-height: 190px; overflow: auto; border-top: 1px solid var(--line); }.trend-table-wrap table { width: 100%; border-collapse: collapse; font-size: 9px; }.trend-table-wrap th, .trend-table-wrap td { padding: 6px 9px; text-align: right; border-bottom: 1px solid var(--line); }.trend-table-wrap th { color: var(--muted); background: #eeece5; position: sticky; top: 0; }.trend-table-wrap th:first-child, .trend-table-wrap td:first-child { text-align: left; }.breach { color: var(--red); font-weight: 700; }
.distribution-panel { min-height: 408px; }.distribution-groups { padding: 13px; }.distribution-groups > div + div { margin-top: 16px; }.bar-list { display: grid; gap: 6px; }.bar-row { display: grid; grid-template-columns: 68px 1fr 25px; gap: 7px; align-items: center; font-size: 9px; }.bar-row > span { color: #555b56; }.bar-row > div { height: 7px; background: #e5e2da; }.bar-row i { display: block; height: 100%; background: var(--teal); }.bar-row b { text-align: right; font: 600 9px 'IBM Plex Mono', monospace; }.mini-empty { color: var(--muted); font-size: 10px; }.definition-panel { grid-column: 1/-1; }.definition-panel > header > svg { color: var(--teal); }.definition-panel dl { display: grid; grid-template-columns: repeat(2, 1fr); margin: 0; }.definition-panel dl div { display: grid; grid-template-columns: 170px 1fr; gap: 10px; padding: 9px 13px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }.definition-panel dl div:nth-child(2n) { border-right: 0; }.definition-panel dt { color: var(--teal-dark); font-size: 9px; }.definition-panel dd { margin: 0; color: #555b56; line-height: 1.5; font-size: 10px; }.definition-panel footer { display: flex; gap: 14px; align-items: center; padding: 9px 13px; color: var(--muted); font-size: 9px; }.definition-panel footer b { color: var(--ink); font-weight: 600; }
@media (max-width: 1050px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); }.dashboard-grid { grid-template-columns: 1fr; }.definition-panel { grid-column: auto; }.definition-panel dl { grid-template-columns: 1fr; }.definition-panel dl div { border-right: 0; } }
@media (max-width: 700px) { .dashboard-controls { align-items: flex-start; flex-direction: column; }.dashboard-controls form { width: 100%; display: grid; grid-template-columns: 1fr auto 1fr; }.dashboard-controls input { width: 100%; }.dashboard-controls .button { grid-column: 1/-1; }.kpi-grid { grid-template-columns: 1fr 1fr; }.trend-chart { height: 220px; }.trend-chart svg { height: 180px; }.definition-panel dl div { grid-template-columns: 1fr; }.definition-panel footer { flex-wrap: wrap; } }
@media (max-width: 460px) { .kpi-grid { grid-template-columns: 1fr; }.chart-legend { display: none; }.trend-chart { padding-left: 6px; padding-right: 6px; }.trend-table-wrap { display: none; } }
</style>
