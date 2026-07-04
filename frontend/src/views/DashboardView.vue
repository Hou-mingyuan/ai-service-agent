<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'

const data = ref<any>(null)
const loading = ref(false)

const statusLabel: Record<string, string> = {
  OPEN: '待处理', IN_PROGRESS: '处理中', PENDING: '挂起', RESOLVED: '已解决', CLOSED: '已关闭'
}
const catLabel: Record<string, string> = {
  ORDER: '订单', LOGISTICS: '物流', POLICY: '保单', REFUND: '退款', COMPLAINT: '投诉', OTHER: '其它'
}
const prioLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }

async function load() {
  loading.value = true
  try {
    data.value = await api.get('/api/dashboard/overview')
  } finally {
    loading.value = false
  }
}
onMounted(load)

const kpis = computed(() => {
  if (!data.value) return []
  const d = data.value
  return [
    { label: '会话总数', value: d.conversations.total, icon: '💬', tone: 'blue' },
    { label: '解决率', value: d.conversations.resolveRate + '%', icon: '✅', tone: 'green' },
    { label: '转人工会话', value: d.conversations.human, icon: '🙋', tone: 'amber' },
    { label: '工单总数', value: d.tickets.total, icon: '🎫', tone: 'purple' },
    { label: '待处理工单', value: d.tickets.open, icon: '⏳', tone: 'amber' },
    { label: '满意度', value: d.satisfaction.avgRating + ' / 5', icon: '⭐', tone: 'green' },
    { label: '工具调用次数', value: d.messages.toolCalls, icon: '🔧', tone: 'blue' },
    { label: '消息总数', value: d.messages.total, icon: '📨', tone: 'gray' }
  ]
})

function bars(obj: Record<string, any>, labelMap: Record<string, string>) {
  if (!obj) return []
  const entries = Object.entries(obj).map(([k, v]) => ({ key: labelMap[k] || k, value: Number(v) }))
  const max = Math.max(1, ...entries.map((e) => e.value))
  return entries.map((e) => ({ ...e, pct: Math.round((e.value / max) * 100) }))
}
</script>

<template>
  <div class="dash">
    <div class="dash-head">
      <div>
        <h2>运营数据看板</h2>
        <span class="sub" v-if="data">模型供应方：<b>{{ data.llmProvider }}</b></span>
      </div>
      <button class="btn-outline" @click="load" :disabled="loading">
        {{ loading ? '刷新中…' : '↻ 刷新' }}
      </button>
    </div>

    <div v-if="data" class="kpis">
      <div v-for="k in kpis" :key="k.label" class="kpi card" :class="'tone-' + k.tone">
        <div class="kpi-icon">{{ k.icon }}</div>
        <div>
          <div class="kpi-value">{{ k.value }}</div>
          <div class="kpi-label">{{ k.label }}</div>
        </div>
      </div>
    </div>

    <div v-if="data" class="charts">
      <div class="chart card">
        <div class="chart-title">工单按状态分布</div>
        <div v-for="b in bars(data.tickets.byStatus, statusLabel)" :key="b.key" class="bar-row">
          <span class="bar-label">{{ b.key }}</span>
          <div class="bar-track"><div class="bar-fill blue" :style="{ width: b.pct + '%' }"></div></div>
          <span class="bar-val">{{ b.value }}</span>
        </div>
      </div>

      <div class="chart card">
        <div class="chart-title">工单按类别分布</div>
        <div v-for="b in bars(data.tickets.byCategory, catLabel)" :key="b.key" class="bar-row">
          <span class="bar-label">{{ b.key }}</span>
          <div class="bar-track"><div class="bar-fill purple" :style="{ width: b.pct + '%' }"></div></div>
          <span class="bar-val">{{ b.value }}</span>
        </div>
      </div>

      <div class="chart card">
        <div class="chart-title">工单按优先级分布</div>
        <div v-for="b in bars(data.tickets.byPriority, prioLabel)" :key="b.key" class="bar-row">
          <span class="bar-label">{{ b.key }}</span>
          <div class="bar-track"><div class="bar-fill amber" :style="{ width: b.pct + '%' }"></div></div>
          <span class="bar-val">{{ b.value }}</span>
        </div>
      </div>
    </div>

    <div v-else class="empty">加载中…</div>
  </div>
</template>

<style scoped>
.dash { height: 100%; overflow-y: auto; }
.dash-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h2 { margin: 0; font-size: 20px; }
.sub { color: var(--muted); font-size: 13px; }

.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 18px; }
.kpi { padding: 18px; display: flex; align-items: center; gap: 14px; }
.kpi-icon { width: 46px; height: 46px; border-radius: 12px; display: grid; place-items: center; font-size: 22px; background: #f1f5f9; }
.kpi-value { font-size: 24px; font-weight: 700; }
.kpi-label { color: var(--muted); font-size: 13px; }
.tone-blue .kpi-icon { background: #dbeafe; }
.tone-green .kpi-icon { background: #dcfce7; }
.tone-amber .kpi-icon { background: #fef3c7; }
.tone-purple .kpi-icon { background: #ede9fe; }

.charts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.chart { padding: 18px; }
.chart-title { font-weight: 700; margin-bottom: 14px; }
.bar-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.bar-label { width: 56px; font-size: 13px; color: #475569; flex-shrink: 0; }
.bar-track { flex: 1; height: 10px; background: #f1f5f9; border-radius: 999px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 999px; transition: width 0.5s ease; }
.bar-fill.blue { background: linear-gradient(90deg, #60a5fa, #3b82f6); }
.bar-fill.purple { background: linear-gradient(90deg, #a78bfa, #7c3aed); }
.bar-fill.amber { background: linear-gradient(90deg, #fbbf24, #d97706); }
.bar-val { width: 28px; text-align: right; font-weight: 600; font-size: 13px; }
.empty { color: var(--muted); text-align: center; padding: 60px 0; }

@media (max-width: 1000px) {
  .kpis { grid-template-columns: repeat(2, 1fr); }
  .charts { grid-template-columns: 1fr; }
}
</style>
