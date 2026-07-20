import { computed, reactive } from 'vue'
import { ApiError, api, resetCsrf } from './api'
import type { HealthInfo, LoginResponse, SessionUser, UserRole } from './types'

export const sessionState = reactive<{
  user: SessionUser | null
  health: HealthInfo | null
  bootstrapped: boolean
  loading: boolean
}>({
  user: null,
  health: null,
  bootstrapped: false,
  loading: false
})

let bootstrapPromise: Promise<void> | null = null

export async function bootstrapSession() {
  if (sessionState.bootstrapped) return
  if (bootstrapPromise) return bootstrapPromise
  bootstrapPromise = (async () => {
    sessionState.loading = true
    const health = api.get<HealthInfo>('/api/health', { retry: 1 }).catch(() => null)
    try {
      const response = await api.get<LoginResponse | null>('/api/auth/session', { retry: 0 })
      sessionState.user = response ? toUser(response) : null
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error
      sessionState.user = null
    } finally {
      sessionState.health = await health
      sessionState.bootstrapped = true
      sessionState.loading = false
      bootstrapPromise = null
    }
  })()
  return bootstrapPromise
}

export async function refreshHealth() {
  sessionState.health = await api.get<HealthInfo>('/api/health', { retry: 1, dedupe: false })
}

export async function login(username: string, password: string) {
  sessionState.loading = true
  try {
    resetCsrf()
    const response = await api.post<LoginResponse>(
      '/api/auth/login',
      { username, password },
      { csrf: false }
    )
    sessionState.user = toUser(response)
    sessionState.bootstrapped = true
    resetCsrf()
    return sessionState.user
  } finally {
    sessionState.loading = false
  }
}

export async function logout() {
  try {
    await api.post<void>('/api/auth/logout')
  } finally {
    clearSession()
  }
}

export function clearSession() {
  sessionState.user = null
  sessionState.bootstrapped = true
  resetCsrf()
}

export function hasPermission(permission: string) {
  return sessionState.user?.permissions.includes(permission) ?? false
}

export function homeForRole(role?: UserRole) {
  if (role === 'customer') return '/chat'
  if (role === 'agent') return '/workspace'
  if (role === 'supervisor') return '/workspace'
  if (role === 'admin') return '/dashboard'
  return '/login'
}

export function useSession() {
  return {
    state: sessionState,
    user: computed(() => sessionState.user),
    health: computed(() => sessionState.health),
    isDemo: computed(() => sessionState.health?.mode === 'DEMO'),
    has: hasPermission,
    login,
    logout,
    refreshHealth
  }
}

function toUser(response: LoginResponse): SessionUser {
  return {
    username: response.username,
    displayName: response.displayName,
    role: response.role,
    permissions: [...response.permissions],
    expiresInMinutes: response.expiresInMinutes
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('csagent:auth-expired', () => clearSession())
}
