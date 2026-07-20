<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { AlertTriangle, Archive, BookOpen, Database, FilePlus2, FileText, RefreshCw, Search, ShieldCheck } from '@lucide/vue'
import { api } from '../api'
import { hasPermission, sessionState } from '../session'
import type { KnowledgeDocument, KnowledgeSearchResult } from '../types'
import { errorMessage, formatDate, statusLabels, statusTone } from '../ui'

const documents = ref<KnowledgeDocument[]>([])
const results = ref<KnowledgeSearchResult[]>([])
const query = ref('')
const loading = ref(true)
const searching = ref(false)
const error = ref('')
const notice = ref('')
const showIngest = ref(false)
const archiveTarget = ref<KnowledgeDocument | null>(null)
const actionLoading = ref(false)
const form = reactive({ title: '', sourceUri: '', content: '' })
const canWrite = computed(() => hasPermission('knowledge:write'))
const readyCount = computed(() => documents.value.filter((item) => item.status === 'READY').length)
const dataSource = computed(() => sessionState.health?.knowledge.source || 'local-lexical')

onMounted(loadDocuments)

async function loadDocuments() {
  loading.value = true
  error.value = ''
  try {
    documents.value = await api.get<KnowledgeDocument[]>('/api/knowledge/documents', { dedupe: false })
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function searchKnowledge() {
  const text = query.value.trim()
  if (!text || searching.value) return
  searching.value = true
  error.value = ''
  try {
    results.value = await api.get<KnowledgeSearchResult[]>(`/api/knowledge/search?q=${encodeURIComponent(text)}&topK=8`, { dedupe: false })
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    searching.value = false
  }
}

async function ingest() {
  if (!form.title.trim() || !form.content.trim() || actionLoading.value) return
  actionLoading.value = true
  error.value = ''
  try {
    await api.post<KnowledgeDocument>('/api/knowledge/documents', {
      title: form.title.trim(), sourceUri: form.sourceUri.trim() || null, content: form.content.trim()
    })
    showIngest.value = false
    form.title = ''; form.sourceUri = ''; form.content = ''
    notice.value = '文档已完成确定性分块并进入可检索状态。'
    await loadDocuments()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    actionLoading.value = false
  }
}

async function archive() {
  if (!archiveTarget.value || actionLoading.value) return
  actionLoading.value = true
  error.value = ''
  try {
    await api.delete<KnowledgeDocument>(`/api/knowledge/documents/${archiveTarget.value.id}`)
    notice.value = `“${archiveTarget.value.title}”已归档，不再参与检索。`
    archiveTarget.value = null
    await loadDocuments()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <div class="knowledge-page">
    <section class="knowledge-summary panel">
      <div><span class="summary-icon"><BookOpen /></span><span><small>DOCUMENTS</small><b>{{ documents.length }}</b><em>租户内文档</em></span></div>
      <div><span class="summary-icon"><ShieldCheck /></span><span><small>SEARCHABLE</small><b>{{ readyCount }}</b><em>当前可检索</em></span></div>
      <div><span class="summary-icon"><Database /></span><span><small>RETRIEVAL</small><b class="source-name">{{ dataSource }}</b><em>{{ sessionState.health?.knowledge.mock ? 'Mock 标识已开启' : '真实来源' }}</em></span></div>
      <button v-if="canWrite" class="button primary" @click="showIngest = true"><FilePlus2 :size="16" />录入知识文档</button>
    </section>

    <div v-if="error" class="knowledge-alert danger"><AlertTriangle :size="16" />{{ error }}<button @click="error = ''">关闭</button></div>
    <div v-if="notice" class="knowledge-alert success"><ShieldCheck :size="16" />{{ notice }}<button @click="notice = ''">关闭</button></div>

    <section class="search-panel panel">
      <header class="panel-header"><div><span class="panel-index">RETRIEVAL TEST</span><h2>检索验证</h2><p>按与 Agent 相同的租户隔离检索链路查看来源、片段与得分。</p></div></header>
      <form class="knowledge-search" @submit.prevent="searchKnowledge"><Search :size="18" /><input v-model="query" maxlength="1000" placeholder="输入自然语言问题，例如：退货需要满足什么条件？" /><button class="button primary" :disabled="searching || !query.trim()">{{ searching ? '检索中…' : '检索知识' }}</button></form>
      <div v-if="searching" class="loading-line" />
      <div v-if="results.length" class="result-grid"><article v-for="result in results" :key="result.chunkId" class="search-result"><header><span class="citation">[{{ result.citation }}]</span><b>{{ result.title }}</b><span class="score">{{ Math.round(result.score * 100) }}%</span></header><p>{{ result.excerpt }}</p><footer><span class="mono">DOC {{ result.documentId }} / CHUNK {{ result.chunkId }}</span><a v-if="result.sourceUri" :href="result.sourceUri" target="_blank" rel="noreferrer">查看来源</a><span v-else>内部文档</span><span>{{ result.dataSource }}</span></footer></article></div>
      <div v-else-if="query && !searching" class="empty-state">没有达到检索阈值的结果；Agent 会将此类问题转人工，不会编造答案。</div>
      <div v-else class="search-placeholder"><Search :size="30" /><p>输入问题后显示命中的确定性分块与来源证据。</p></div>
    </section>

    <section class="document-panel panel">
      <header class="panel-header"><div><span class="panel-index">CORPUS</span><h2>知识文档</h2><p>归档为软删除，保留历史记录与审计证据。</p></div><button class="icon-button" aria-label="刷新文档" @click="loadDocuments"><RefreshCw /></button></header>
      <div v-if="loading" class="loading-line" />
      <div class="data-table-wrap"><table class="data-table"><thead><tr><th>ID</th><th>文档</th><th>来源</th><th>状态</th><th>校验和</th><th>录入人</th><th>更新时间</th><th v-if="canWrite">操作</th></tr></thead><tbody><tr v-for="document in documents" :key="document.id"><td class="mono">{{ document.id }}</td><td><div class="doc-title"><FileText :size="15" /><span><b>{{ document.title }}</b><small>{{ document.content.slice(0, 80) }}{{ document.content.length > 80 ? '…' : '' }}</small></span></div></td><td><a v-if="document.sourceUri" :href="document.sourceUri" target="_blank" rel="noreferrer">{{ document.sourceUri }}</a><span v-else>内部文档</span></td><td><span class="badge" :class="statusTone(document.status)">{{ statusLabels[document.status] }}</span></td><td class="mono checksum">{{ document.checksum.slice(0, 12) }}…</td><td>{{ document.createdBy }}</td><td>{{ formatDate(document.updatedAt) }}</td><td v-if="canWrite"><button class="button small danger" :disabled="document.status === 'ARCHIVED'" @click="archiveTarget = document"><Archive :size="14" />归档</button></td></tr></tbody></table></div>
      <div v-if="!loading && !documents.length" class="empty-state">租户内尚无知识文档</div>
    </section>
  </div>

  <div v-if="showIngest" class="modal-backdrop" @click.self="showIngest = false"><section class="knowledge-dialog" role="dialog" aria-modal="true" aria-labelledby="ingest-title"><span class="panel-index">INGEST DOCUMENT</span><h2 id="ingest-title">录入知识文档</h2><p>文本将使用固定规则分块并计算校验和；相同内容重复提交不会生成重复文档。</p><label>标题<input v-model="form.title" maxlength="255" placeholder="例如：退换货服务规则" /></label><label>来源地址（可选）<input v-model="form.sourceUri" maxlength="500" placeholder="https://docs.example.com/policy" /></label><label>正文<textarea v-model="form.content" rows="10" maxlength="100000" placeholder="粘贴经过确认的知识正文" /></label><small>{{ form.content.length }} / 100000</small><footer><button class="button" :disabled="actionLoading" @click="showIngest = false">取消</button><button class="button primary" :disabled="actionLoading || !form.title.trim() || !form.content.trim()" @click="ingest">{{ actionLoading ? '处理中…' : '录入并分块' }}</button></footer></section></div>
  <div v-if="archiveTarget" class="modal-backdrop" @click.self="archiveTarget = null"><section class="archive-dialog" role="dialog" aria-modal="true"><span class="dialog-warning"><AlertTriangle /></span><h2>确认归档知识文档？</h2><p>“{{ archiveTarget.title }}”归档后立即停止参与新检索，但已有引用和审计记录会保留。</p><footer><button class="button" :disabled="actionLoading" @click="archiveTarget = null">取消</button><button class="button danger" :disabled="actionLoading" @click="archive">{{ actionLoading ? '归档中…' : '确认归档' }}</button></footer></section></div>
</template>

<style scoped>
.knowledge-page { display: grid; grid-template-columns: minmax(360px, .9fr) minmax(520px, 1.35fr); gap: 12px; align-items: start; }.knowledge-summary { grid-column: 1/-1; min-height: 82px; display: grid; grid-template-columns: repeat(3, 1fr) auto; align-items: stretch; }.knowledge-summary > div { display: flex; align-items: center; gap: 11px; padding: 13px 16px; border-right: 1px solid var(--line); }.summary-icon { width: 38px; height: 38px; display: grid; place-items: center; color: var(--teal); background: var(--teal-soft); border: 1px solid #9fc8c1; }.summary-icon svg { width: 19px; }.knowledge-summary span span, .knowledge-summary small, .knowledge-summary b, .knowledge-summary em { display: block; }.knowledge-summary small { color: var(--muted); font: 9px 'IBM Plex Mono', monospace; }.knowledge-summary b { font-size: 21px; line-height: 1.1; }.knowledge-summary b.source-name { font-size: 13px; margin: 3px 0; }.knowledge-summary em { color: var(--muted); font-size: 9px; font-style: normal; }.knowledge-summary > .button { align-self: center; margin: 0 15px; }
.knowledge-alert { grid-column: 1/-1; display: flex; align-items: center; gap: 7px; padding: 8px 11px; border: 1px solid; font-size: 11px; }.knowledge-alert button { margin-left: auto; color: inherit; background: transparent; text-decoration: underline; }.knowledge-alert.danger { color: #842b2b; background: var(--red-soft); border-color: #d7a19a; }.knowledge-alert.success { color: #164f4a; background: var(--teal-soft); border-color: #9fc8c1; }
.search-panel, .document-panel { min-height: 450px; }.knowledge-search { display: grid; grid-template-columns: auto 1fr auto; gap: 8px; align-items: center; padding: 12px; border-bottom: 1px solid var(--line); }.knowledge-search > svg { color: var(--teal); }.knowledge-search input { height: 38px; }.result-grid { display: grid; gap: 8px; padding: 12px; max-height: 540px; overflow: auto; }.search-result { padding: 11px; background: #f2f0e9; border: 1px solid var(--line); }.search-result header { display: grid; grid-template-columns: auto 1fr auto; gap: 7px; align-items: center; }.citation { color: #fff; background: var(--teal); padding: 3px 5px; font: 9px 'IBM Plex Mono', monospace; }.search-result header b { font-size: 12px; }.score { color: var(--teal); font: 600 10px 'IBM Plex Mono', monospace; }.search-result p { margin: 8px 0; color: #4f5551; line-height: 1.65; font-size: 11px; }.search-result footer { display: flex; flex-wrap: wrap; gap: 8px; color: var(--muted); font-size: 9px; }.search-result footer a { color: var(--teal-dark); }.search-placeholder { min-height: 310px; display: grid; place-content: center; justify-items: center; color: var(--muted); }.search-placeholder p { margin: 10px 0; font-size: 11px; }
.document-panel { overflow: hidden; }.document-panel .data-table-wrap { max-height: 540px; }.doc-title { min-width: 210px; display: flex; gap: 7px; align-items: flex-start; }.doc-title svg { flex: 0 0 auto; color: var(--teal); }.doc-title b, .doc-title small { display: block; }.doc-title b { font-size: 11px; }.doc-title small { max-width: 300px; color: var(--muted); margin-top: 3px; line-height: 1.45; font-size: 9px; }.data-table a { max-width: 180px; display: block; color: var(--teal-dark); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.checksum { color: var(--muted); font-size: 9px; }
.modal-backdrop { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 18px; background: rgba(19,23,20,.62); }.knowledge-dialog, .archive-dialog { width: min(580px, 100%); max-height: 92vh; overflow: auto; padding: 24px; background: var(--surface); border: 1px solid var(--line-strong); box-shadow: 0 24px 60px rgba(0,0,0,.28); }.knowledge-dialog h2, .archive-dialog h2 { margin: 6px 0; }.knowledge-dialog > p, .archive-dialog > p { color: var(--muted); line-height: 1.6; font-size: 11px; }.knowledge-dialog label { display: block; margin-top: 12px; color: #555b56; font-size: 10px; }.knowledge-dialog input, .knowledge-dialog textarea { margin-top: 5px; }.knowledge-dialog > small { display: block; color: var(--muted); text-align: right; }.knowledge-dialog footer, .archive-dialog footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 17px; }.archive-dialog { width: min(440px, 100%); }.dialog-warning { width: 43px; height: 43px; display: grid; place-items: center; color: var(--red); background: var(--red-soft); border: 1px solid #d5a19b; margin-bottom: 12px; }
@media (max-width: 980px) { .knowledge-page { grid-template-columns: 1fr; }.knowledge-summary { grid-template-columns: repeat(3, 1fr); }.knowledge-summary > .button { grid-column: 1/-1; margin: 10px 15px 15px; }.search-panel, .document-panel { min-height: auto; } }
@media (max-width: 620px) { .knowledge-summary { grid-template-columns: 1fr; }.knowledge-summary > div { border-right: 0; border-bottom: 1px solid var(--line); }.knowledge-search { grid-template-columns: auto 1fr; }.knowledge-search .button { grid-column: 1/-1; }.data-table th:nth-child(3), .data-table td:nth-child(3), .data-table th:nth-child(5), .data-table td:nth-child(5), .data-table th:nth-child(6), .data-table td:nth-child(6) { display: none; }.modal-backdrop { align-items: end; padding: 0; }.knowledge-dialog, .archive-dialog { padding: 20px; } }
</style>
