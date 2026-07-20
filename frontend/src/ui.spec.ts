import { describe, expect, it } from 'vitest'
import { clientId, conversationActionsDisabled, errorMessage, formatDuration, statusTone } from './ui'
import { ApiError } from './api'

describe('ui helpers', () => {
  it('creates backend-compatible idempotency keys', () => {
    const first = clientId('chat')
    const second = clientId('chat')
    expect(first).toMatch(/^[A-Za-z0-9._:-]{8,64}$/)
    expect(second).not.toBe(first)
  })

  it('formats metric durations at useful units', () => {
    expect(formatDuration(8.2)).toBe('8.2 秒')
    expect(formatDuration(90)).toBe('1.5 分钟')
    expect(formatDuration(7200)).toBe('2.0 小时')
  })

  it('preserves request IDs in normalized API errors', () => {
    expect(errorMessage(new ApiError('无权限', 403, 403, 'req-42'))).toBe('无权限（请求 req-42）')
    expect(statusTone('PENDING_CONFIRMATION')).toBe('warning')
    expect(statusTone('CLOSED')).toBe('success')
  })

  it('keeps every conversation action disabled after closure', () => {
    expect(conversationActionsDisabled('CLOSED')).toBe(true)
    expect(conversationActionsDisabled('HUMAN', true)).toBe(true)
    expect(conversationActionsDisabled('BOT')).toBe(false)
  })
})
