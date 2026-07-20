<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AlertTriangle, ChevronLeft, ChevronRight, Eye, Filter, RefreshCw, ShieldCheck, X } from '@lucide/vue'
import { api } from '../api'
import type { AuditLog, PageResult } from '../types'
import { errorMessage, formatDate, roleLabels, statusTone } from '../ui'

const logs = ref<AuditLog[]>([])
const total = ref(0)
const pages = ref(0)
const page = ref(1)
const size = 30
const action = ref('')
const actor = ref('')
const loading = ref(true)
const error = ref('')
const selected = ref<AuditLog | null>(null)
const actions = computed(() => [...new Set(logs.value.map((item) => item.action))].sort())

onMounted(load)

async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), size: String(size) })
    if (action.value) params.set('action', action.value)
    if (actor.value.trim()) params.set('actor', actor.value.trim())
    const result = await api.get<PageResult<AuditLog>>(`/api/audit?${params}`, { dedupe: false })
    logs.value = result.records
    total.value = result.total
    pages.value = result.pages
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function changePage(next: number) {
  if (next < 1 || next > pages.value) return
  page.value = next
  await load()
}

function details(value?: string | null) {
  if (!value) return '无附加详情'
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
}
</script>

<template>
  <div class="audit-page">
    <section class="audit-banner panel"><span class="banner-icon"><ShieldCheck /></span><div><span class="panel-index">IMMUTABLE EVIDENCE</span><h2>操作证据链</h2><p>身份、资源、结果、幂等键与请求编号统一记录；令牌、密码及敏感业务字段在入库前脱敏。</p></div><dl><div><dt>当前记录</dt><dd>{{ total }}</dd></div><div><dt>当前页</dt><dd>{{ page }} / {{ Math.max(1, pages) }}</dd></div></dl></section>

    <section class="audit-panel panel">
      <header class="panel-header"><div><span class="panel-index">AUDIT LOG</span><h2>审计日志</h2><p>按操作者和动作过滤，查看单次请求的结构化详情。</p></div><button class="icon-button" aria-label="刷新审计日志" @click="load()"><RefreshCw /></button></header>
      <form class="audit-filters" @submit.prevent="load(true)"><Filter :size="16" /><select v-model="action"><option value="">全部动作</option><option v-for="value in actions" :key="value" :value="value">{{ value }}</option></select><input v-model="actor" maxlength="64" placeholder="操作者账号" /><button class="button primary">应用筛选</button><button type="button" class="button" @click="action = ''; actor = ''; load(true)">清除</button></form>
      <div v-if="loading" class="loading-line" />
      <div v-if="error" class="audit-alert"><AlertTriangle :size="15" />{{ error }}<button @click="error = ''">关闭</button></div>
      <div class="data-table-wrap audit-table-wrap"><table class="data-table"><thead><tr><th>时间</th><th>结果</th><th>操作者</th><th>动作</th><th>资源</th><th>幂等键</th><th>请求编号</th><th>详情</th></tr></thead><tbody><tr v-for="log in logs" :key="log.id"><td class="time-cell">{{ formatDate(log.createdAt) }}</td><td><span class="badge" :class="statusTone(log.outcome === 'SUCCESS' ? 'SUCCEEDED' : log.outcome === 'DENIED' ? 'REJECTED' : log.outcome)">{{ log.outcome }}</span></td><td><b>{{ log.actorUsername }}</b><small>{{ roleLabels[log.actorRole] || log.actorRole }}</small></td><td class="mono action-cell">{{ log.action }}</td><td><span>{{ log.resourceType }}</span><small v-if="log.resourceId" class="mono">#{{ log.resourceId }}</small></td><td class="mono truncate">{{ log.idempotencyKey || '—' }}</td><td class="mono truncate">{{ log.requestId }}</td><td><button class="button small" @click="selected = log"><Eye :size="14" />查看</button></td></tr></tbody></table></div>
      <div v-if="!loading && !logs.length" class="empty-state">当前筛选下没有审计记录</div>
      <footer class="pagination"><button class="icon-button" :disabled="page <= 1" @click="changePage(page - 1)"><ChevronLeft /></button><span class="mono">第 {{ page }} 页 · 共 {{ total }} 条</span><button class="icon-button" :disabled="page >= pages" @click="changePage(page + 1)"><ChevronRight /></button></footer>
    </section>
  </div>

  <div v-if="selected" class="drawer-backdrop" @click.self="selected = null"><aside class="audit-drawer" role="dialog" aria-modal="true" aria-labelledby="audit-title"><header><div><span class="panel-index">AUDIT #{{ selected.id }}</span><h2 id="audit-title">审计详情</h2></div><button class="icon-button" aria-label="关闭" @click="selected = null"><X /></button></header><div class="drawer-body"><section><div class="section-label">IDENTITY</div><dl><div><dt>操作者</dt><dd>{{ selected.actorUsername }}</dd></div><div><dt>角色</dt><dd>{{ roleLabels[selected.actorRole] || selected.actorRole }}</dd></div><div><dt>结果</dt><dd><span class="badge" :class="statusTone(selected.outcome === 'SUCCESS' ? 'SUCCEEDED' : 'REJECTED')">{{ selected.outcome }}</span></dd></div><div><dt>发生时间</dt><dd>{{ formatDate(selected.createdAt) }}</dd></div></dl></section><section><div class="section-label">TRACE</div><dl><div><dt>动作</dt><dd class="mono">{{ selected.action }}</dd></div><div><dt>资源</dt><dd class="mono">{{ selected.resourceType }} / {{ selected.resourceId || '—' }}</dd></div><div><dt>幂等键</dt><dd class="mono break">{{ selected.idempotencyKey || '—' }}</dd></div><div><dt>请求编号</dt><dd class="mono break">{{ selected.requestId }}</dd></div></dl></section><section><div class="section-label">REDACTED DETAILS</div><pre>{{ details(selected.detailsJson) }}</pre><p class="redaction-note"><ShieldCheck :size="14" />该内容由服务端脱敏后返回，前端不尝试还原敏感字段。</p></section></div></aside></div>
</template>

<style scoped>
.audit-page { display: grid; gap: 12px; }.audit-banner { min-height: 92px; display: grid; grid-template-columns: auto 1fr auto; gap: 14px; align-items: center; padding: 15px 18px; }.banner-icon { width: 45px; height: 45px; display: grid; place-items: center; color: var(--teal); background: var(--teal-soft); border: 1px solid #9fc8c1; }.audit-banner h2 { margin: 3px 0; font-size: 18px; }.audit-banner p { color: var(--muted); margin: 0; font-size: 11px; }.audit-banner dl { display: grid; grid-template-columns: 1fr 1fr; margin: 0; border: 1px solid var(--line); }.audit-banner dl div { min-width: 105px; padding: 8px 11px; border-right: 1px solid var(--line); }.audit-banner dl div:last-child { border-right: 0; }.audit-banner dt { color: var(--muted); font-size: 9px; }.audit-banner dd { margin: 2px 0 0; font: 600 14px 'IBM Plex Mono', monospace; }
.audit-panel { min-height: 560px; overflow: hidden; }.audit-filters { display: grid; grid-template-columns: auto 210px 210px auto auto; align-items: center; gap: 7px; padding: 9px 12px; border-bottom: 1px solid var(--line); }.audit-filters > svg { color: var(--teal); }.audit-filters input, .audit-filters select { height: 35px; font-size: 11px; }.audit-alert { display: flex; align-items: center; gap: 7px; padding: 8px 11px; color: #842b2b; background: var(--red-soft); border-bottom: 1px solid #d7a19a; font-size: 10px; }.audit-alert button { margin-left: auto; color: inherit; background: transparent; text-decoration: underline; }.audit-table-wrap { max-height: calc(100vh - 365px); min-height: 355px; }.data-table td small { display: block; color: var(--muted); margin-top: 2px; font-size: 9px; }.time-cell { white-space: nowrap; }.action-cell { color: var(--teal-dark); font-size: 10px; }.pagination { min-height: 48px; display: flex; justify-content: center; align-items: center; gap: 12px; border-top: 1px solid var(--line); }.pagination .icon-button { width: 31px; height: 31px; }.pagination span { color: var(--muted); font-size: 10px; }
.drawer-backdrop { position: fixed; inset: 0; z-index: 100; background: rgba(18,22,19,.58); }.audit-drawer { width: min(520px, 94vw); height: 100%; margin-left: auto; overflow: auto; background: var(--surface); border-left: 1px solid var(--line-strong); box-shadow: -24px 0 55px rgba(0,0,0,.2); }.audit-drawer > header { min-height: 68px; display: flex; justify-content: space-between; align-items: center; padding: 13px 16px; color: #fff; background: #252c27; }.audit-drawer h2 { margin: 3px 0 0; font-size: 18px; }.audit-drawer header .icon-button { border-color: #5a635d; }.drawer-body { padding: 16px; }.drawer-body section { margin-bottom: 18px; }.drawer-body dl { margin: 0; border: 1px solid var(--line); }.drawer-body dl div { display: grid; grid-template-columns: 110px 1fr; gap: 8px; padding: 8px 10px; border-bottom: 1px solid var(--line); font-size: 11px; }.drawer-body dl div:last-child { border-bottom: 0; }.drawer-body dt { color: var(--muted); }.drawer-body dd { margin: 0; text-align: right; }.break { overflow-wrap: anywhere; }.drawer-body pre { max-height: 340px; overflow: auto; margin: 0; padding: 12px; white-space: pre-wrap; overflow-wrap: anywhere; color: #d9e5dd; background: #252c27; border: 1px solid #4b544e; font: 10px/1.6 'IBM Plex Mono', monospace; }.redaction-note { display: flex; gap: 6px; color: var(--teal-dark); margin: 8px 0 0; font-size: 10px; }
@media (max-width: 800px) { .audit-banner { grid-template-columns: auto 1fr; }.audit-banner dl { grid-column: 1/-1; }.audit-filters { grid-template-columns: auto 1fr 1fr; }.audit-filters .button { grid-row: 2; }.audit-table-wrap { max-height: 500px; }.data-table th:nth-child(5), .data-table td:nth-child(5), .data-table th:nth-child(6), .data-table td:nth-child(6), .data-table th:nth-child(7), .data-table td:nth-child(7) { display: none; } }
@media (max-width: 520px) { .audit-banner { grid-template-columns: 1fr; }.banner-icon { display: none; }.audit-filters { grid-template-columns: 1fr 1fr; }.audit-filters > svg { display: none; }.audit-filters select, .audit-filters input { grid-column: 1/-1; }.audit-filters .button { grid-row: auto; }.audit-drawer { width: 100%; }.data-table th:nth-child(3), .data-table td:nth-child(3) { display: none; } }
</style>
