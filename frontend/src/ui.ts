import { ApiError } from './api'

export const statusLabels: Record<string, string> = {
  BOT: '机器人服务',
  HUMAN_PENDING: '等待坐席',
  HUMAN: '人工服务',
  CLOSED: '已结束',
  OPEN: '待处理',
  IN_PROGRESS: '处理中',
  PENDING: '挂起',
  RESOLVED: '已解决',
  READY: '可检索',
  ARCHIVED: '已归档',
  PENDING_CONFIRMATION: '待确认',
  SUCCEEDED: '已执行',
  FAILED: '失败',
  REJECTED: '已拒绝'
}

export const priorityLabels: Record<string, string> = {
  LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急'
}

export const categoryLabels: Record<string, string> = {
  ORDER: '订单', LOGISTICS: '物流', POLICY: '保单', REFUND: '退款', COMPLAINT: '投诉', OTHER: '其他'
}

export const roleLabels: Record<string, string> = {
  customer: '客户', agent: '一线坐席', supervisor: '主管', admin: '管理员'
}

export function formatDate(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 19)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  }).format(date)
}

export function formatDuration(seconds: number) {
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} 秒`
  if (seconds < 3600) return `${(seconds / 60).toFixed(1)} 分钟`
  return `${(seconds / 3600).toFixed(1)} 小时`
}

export function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    const request = error.requestId ? `（请求 ${error.requestId}）` : ''
    return `${error.message}${request}`
  }
  if (error instanceof DOMException && error.name === 'AbortError') return '操作已取消'
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

export function clientId(prefix = 'web') {
  const value = typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
  return `${prefix}:${value}`.slice(0, 64)
}

export function conversationActionsDisabled(status?: string | null, pending = false) {
  return pending || status === 'CLOSED'
}

export function statusTone(status?: string | null) {
  if (['RESOLVED', 'CLOSED', 'READY', 'SUCCEEDED', 'COMPLETED', 'BOT'].includes(status || '')) return 'success'
  if (['URGENT', 'FAILED', 'REJECTED'].includes(status || '')) return 'danger'
  if (['OPEN', 'PENDING', 'PENDING_CONFIRMATION', 'HUMAN_PENDING'].includes(status || '')) return 'warning'
  if (['IN_PROGRESS', 'HUMAN'].includes(status || '')) return 'info'
  return 'neutral'
}
