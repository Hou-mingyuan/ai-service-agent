<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bot, Database, KeyRound, LockKeyhole, ShieldCheck, UserRound } from '@lucide/vue'
import { homeForRole, login, refreshHealth, sessionState } from '../session'
import { errorMessage } from '../ui'

const router = useRouter()
const form = reactive({ username: 'customer', password: 'customer123' })
const error = ref('')
const demoAccounts = [
  { role: '客户', username: 'customer', password: 'customer123', scope: '对话、工具确认、评价' },
  { role: '坐席', username: 'agent', password: 'agent123', scope: '接管、回复、工单流转' },
  { role: '主管', username: 'supervisor', password: 'super123', scope: '全量会话、指派、看板、审计' },
  { role: '管理员', username: 'admin', password: 'admin123', scope: '全部权限、知识维护' }
]
const isDemo = computed(() => sessionState.health?.mode === 'DEMO')

onMounted(() => { void refreshHealth().catch(() => undefined) })

function selectAccount(username: string, password: string) {
  form.username = username
  form.password = password
  error.value = ''
}

async function submit() {
  if (!form.username.trim() || !form.password) return
  error.value = ''
  try {
    const user = await login(form.username.trim(), form.password)
    await refreshHealth().catch(() => undefined)
    await router.replace(homeForRole(user.role))
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-identity">
      <div class="login-brand"><span><Bot :size="28" /></span><div><b>智答服务中枢</b><small>AI SERVICE OPERATIONS</small></div></div>
      <div class="identity-copy">
        <span class="eyebrow">CUSTOMER OPERATIONS PLATFORM</span>
        <h1>让自动服务与人工处置<br />在同一条链路闭环。</h1>
        <p>客户对话、知识检索、工具执行、转人工、工单与审计由统一身份和实时事件连接。</p>
      </div>
      <dl class="runtime-strip">
        <div><dt><ShieldCheck :size="16" />运行模式</dt><dd>{{ isDemo ? 'DEMO' : 'STANDARD' }}</dd></div>
        <div><dt><Database :size="16" />业务数据</dt><dd>{{ sessionState.health?.business.source || '连接中' }}</dd></div>
        <div><dt><KeyRound :size="16" />模型</dt><dd>{{ sessionState.health?.llm.provider || '连接中' }}</dd></div>
      </dl>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <header><span class="panel-index">01 / AUTH</span><h2>登录工作台</h2><p>使用分配给你的账号进入对应职责范围。</p></header>
        <div v-if="isDemo" class="boundary-notice"><Database :size="16" /><span><b>演示边界</b> 当前业务与模型均为 Mock；界面中的数据不代表真实生产操作。</span></div>
        <form @submit.prevent="submit">
          <label><span>账号</span><div class="field-with-icon"><UserRound :size="17" /><input v-model="form.username" autocomplete="username" maxlength="64" /></div></label>
          <label><span>密码</span><div class="field-with-icon"><LockKeyhole :size="17" /><input v-model="form.password" type="password" autocomplete="current-password" maxlength="128" /></div></label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="button primary login-submit" :disabled="sessionState.loading || !form.username.trim() || !form.password">
            {{ sessionState.loading ? '验证身份中…' : '进入工作台' }}
          </button>
        </form>

        <div v-if="isDemo" class="demo-accounts">
          <div class="section-label">DEMO ACCOUNTS · 点击填入</div>
          <button v-for="account in demoAccounts" :key="account.username" type="button" class="account-row" @click="selectAccount(account.username, account.password)">
            <span class="account-role">{{ account.role }}</span>
            <span><b>{{ account.username }}</b><small>{{ account.scope }}</small></span>
            <code>{{ account.password }}</code>
          </button>
        </div>
      </div>
    </section>
  </main>
</template>
