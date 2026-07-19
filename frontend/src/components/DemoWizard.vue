<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DEMO_STEPS,
  dismissDemoWizard,
  loadDemoProgress,
  markStepDone,
  type DemoProgress
} from '../demo'

const emit = defineEmits<{
  sendPrompt: [text: string]
}>()

const route = useRoute()
const router = useRouter()
const progress = ref<DemoProgress>(loadDemoProgress())
const collapsed = ref(false)

const activeIndex = computed(() => {
  const idx = DEMO_STEPS.findIndex((s) => !progress.value.completed.includes(s.id))
  return idx >= 0 ? idx : DEMO_STEPS.length - 1
})

const activeStep = computed(() => DEMO_STEPS[activeIndex.value])
const finished = computed(() => progress.value.completed.length >= DEMO_STEPS.length)
const visible = computed(() => !progress.value.dismissed)

function refresh() {
  progress.value = loadDemoProgress()
}

function completeStep(id: string) {
  markStepDone(id)
  refresh()
}

function onStart() {
  completeStep('welcome')
}

function onSendExample() {
  const step = activeStep.value
  if (!step?.prompt) return
  emit('sendPrompt', step.prompt)
  completeStep(step.id)
}

async function onGoRoute() {
  const step = activeStep.value
  if (!step?.route) return
  if (route.path !== step.route) {
    await router.push(step.route)
  }
  if (step.doneWhen === 'route') {
    completeStep(step.id)
  }
}

function onDismiss() {
  dismissDemoWizard()
  refresh()
}

function onReset() {
  localStorage.removeItem('csagent-demo-progress')
  progress.value = { completed: [], dismissed: false }
}

watch(
  () => route.path,
  (path) => {
    const step = DEMO_STEPS.find((s) => s.route === path && s.doneWhen === 'route')
    if (step && !progress.value.completed.includes(step.id)) {
      completeStep(step.id)
    }
  }
)

onMounted(refresh)

defineExpose({ refresh, onReset })
</script>

<template>
  <aside v-if="visible" class="wizard card" :class="{ collapsed }">
    <div class="wiz-head">
      <div>
        <div class="wiz-title">🎯 Mock 演示向导</div>
        <div class="wiz-sub">零密钥 · 4 步体验 Agent 闭环</div>
      </div>
      <div class="wiz-actions">
        <button class="icon-btn" :title="collapsed ? '展开' : '收起'" @click="collapsed = !collapsed">
          {{ collapsed ? '◀' : '▶' }}
        </button>
        <button class="icon-btn" title="关闭向导" @click="onDismiss">✕</button>
      </div>
    </div>

    <template v-if="!collapsed">
      <div class="steps">
        <div
          v-for="(s, i) in DEMO_STEPS"
          :key="s.id"
          class="step"
          :class="{ done: progress.completed.includes(s.id), active: i === activeIndex && !finished }"
        >
          <span class="step-dot">{{ progress.completed.includes(s.id) ? '✓' : i + 1 }}</span>
          <span class="step-label">{{ s.title.replace(/^[①②③④]\s*/, '') }}</span>
        </div>
      </div>

      <div v-if="finished" class="wiz-body done-panel">
        <b>🎉 演示完成</b>
        <p>您已体验查单、物流、投诉转人工与坐席工单。可在顶部导航自由探索，或重置向导再来一遍。</p>
        <button class="btn-outline" @click="onReset">重置向导</button>
      </div>

      <div v-else class="wiz-body">
        <b>{{ activeStep.title }}</b>
        <p>{{ activeStep.subtitle }}</p>
        <div class="wiz-btns">
          <button v-if="activeStep.id === 'welcome'" class="btn-primary" @click="onStart">开始演示</button>
          <button v-else-if="activeStep.prompt" class="btn-primary" @click="onSendExample">
            发送示例
          </button>
          <button v-if="activeStep.route && activeStep.id !== 'welcome'" class="btn-ghost" @click="onGoRoute">
            {{ activeStep.route === '/tickets' ? '打开坐席工单' : '前往对话页' }}
          </button>
        </div>
      </div>
    </template>
  </aside>
</template>

<style scoped>
.wizard {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #c7d2fe;
  background: linear-gradient(180deg, #eef2ff 0%, #fff 120px);
}
.wizard.collapsed { width: 52px; min-width: 52px; }
.wiz-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--border);
}
.wiz-title { font-weight: 700; font-size: 14px; color: var(--primary-600); }
.wiz-sub { font-size: 12px; color: var(--muted); margin-top: 2px; }
.wiz-actions { display: flex; gap: 4px; }
.icon-btn {
  width: 28px; height: 28px; padding: 0;
  background: #fff; border: 1px solid var(--border);
  border-radius: 8px; font-size: 12px; color: var(--muted);
}
.icon-btn:hover { color: var(--primary); border-color: var(--primary); }

.steps {
  display: flex; gap: 4px; padding: 10px 12px; flex-wrap: wrap;
  border-bottom: 1px solid var(--border);
}
.step {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; color: var(--muted); padding: 4px 8px;
  border-radius: 999px; background: #f8fafc;
}
.step.active { background: var(--primary-soft); color: var(--primary-600); font-weight: 600; }
.step.done { color: var(--success); }
.step-dot {
  width: 16px; height: 16px; border-radius: 50%;
  display: grid; place-items: center; font-size: 10px;
  background: #e2e8f0; font-weight: 700;
}
.step.active .step-dot { background: var(--primary); color: #fff; }
.step.done .step-dot { background: #dcfce7; color: #15803d; }

.wiz-body { padding: 14px; font-size: 13px; line-height: 1.55; }
.wiz-body b { display: block; margin-bottom: 6px; }
.wiz-body p { margin: 0 0 12px; color: #475569; }
.wiz-btns { display: flex; flex-wrap: wrap; gap: 8px; }
.done-panel { text-align: center; }
.done-panel p { text-align: left; }
</style>
