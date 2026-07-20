<script setup lang="ts">
import { LockKeyhole } from '@lucide/vue'
import { homeForRole, sessionState } from '../session'
</script>

<template>
  <section class="state-page panel">
    <LockKeyhole :size="34" />
    <span class="eyebrow">HTTP 403</span>
    <h2>当前角色没有此功能权限</h2>
    <p>服务端仍会执行权限校验。请返回你的职责主页继续操作。</p>
    <RouterLink class="button primary" :to="homeForRole(sessionState.user?.role)">返回职责主页</RouterLink>
  </section>
</template>
