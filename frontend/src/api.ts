import type { RealtimeEnvelope } from './types'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId?: string
}

export class ApiError extends Error {
  status: number
  code: number
  requestId?: string
  payload?: unknown

  constructor(message: string, status: number, code = status, requestId?: string, payload?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
    this.payload = payload
  }
}

interface RequestOptions {
  signal?: AbortSignal
  retry?: number
  csrf?: boolean
  dedupe?: boolean
  headers?: HeadersInit
}

interface CsrfValue {
  headerName: string
  token: string
}

const inFlight = new Map<string, Promise<unknown>>()
let csrfPromise: Promise<CsrfValue> | null = null

function isMutation(method: string) {
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method.toUpperCase())
}

async function csrfToken(): Promise<CsrfValue> {
  if (!csrfPromise) {
    csrfPromise = fetch('/api/auth/csrf', { credentials: 'include' })
      .then(async (response) => {
        const payload = (await response.json()) as ApiResponse<CsrfValue>
        if (!response.ok || payload.code !== 0) {
          throw new ApiError(payload.message || '无法获取请求校验令牌', response.status, payload.code)
        }
        return payload.data
      })
      .catch((error) => {
        csrfPromise = null
        throw error
      })
  }
  try {
    return await csrfPromise
  } finally {
    csrfPromise = null
  }
}

export function resetCsrf() {
  csrfPromise = null
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text()
  let payload: ApiResponse<T> | null = null
  if (text) {
    try {
      payload = JSON.parse(text) as ApiResponse<T>
    } catch {
      if (!response.ok) {
        throw new ApiError('服务返回了无法解析的错误', response.status)
      }
      throw new ApiError('服务返回格式不正确', response.status)
    }
  }
  if (!response.ok || (payload && payload.code !== 0)) {
    const error = new ApiError(
      payload?.message || `请求失败（HTTP ${response.status}）`,
      response.status,
      payload?.code ?? response.status,
      payload?.requestId,
      payload
    )
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('csagent:auth-expired'))
    }
    throw error
  }
  return payload?.data as T
}

async function execute<T>(path: string, init: RequestInit, options: RequestOptions): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(options.headers || init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (isMutation(method) && options.csrf !== false) {
    const csrf = await csrfToken()
    headers.set(csrf.headerName, csrf.token)
  }

  const attempts = method === 'GET' ? Math.max(1, (options.retry ?? 1) + 1) : 1
  let lastError: unknown
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(path, {
        ...init,
        method,
        headers,
        signal: options.signal,
        credentials: 'include'
      })
      if (response.status >= 500 && attempt < attempts - 1) {
        await delay(250 * 2 ** attempt, options.signal)
        continue
      }
      return await parseResponse<T>(response)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      lastError = error
      const retryable = !(error instanceof ApiError) || error.status >= 500
      if (!retryable || attempt === attempts - 1) throw error
      await delay(250 * 2 ** attempt, options.signal)
    }
  }
  throw lastError
}

export function request<T>(path: string, init: RequestInit = {}, options: RequestOptions = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const shouldDedupe = method === 'GET' && options.dedupe !== false && !options.signal
  const key = `${method}:${path}`
  if (shouldDedupe && inFlight.has(key)) {
    return inFlight.get(key) as Promise<T>
  }
  const promise = execute<T>(path, init, options).finally(() => inFlight.delete(key))
  if (shouldDedupe) inFlight.set(key, promise)
  return promise
}

export const api = {
  get: <T>(path: string, options: RequestOptions = {}) => request<T>(path, {}, options),
  post: <T>(path: string, body?: unknown, options: RequestOptions = {}) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }, options),
  delete: <T>(path: string, options: RequestOptions = {}) =>
    request<T>(path, { method: 'DELETE' }, options)
}

export type SseHandler = (event: string, data: Record<string, unknown>) => void

export async function chatStream(
  body: { conversationId?: number | null; clientMessageId: string; message: string },
  onEvent: SseHandler,
  signal?: AbortSignal
): Promise<void> {
  const csrf = await csrfToken()
  const response = await fetch('/api/chat', {
    method: 'POST',
    credentials: 'include',
    signal,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      [csrf.headerName]: csrf.token
    },
    body: JSON.stringify(body)
  })
  if (!response.ok) {
    const text = await response.text()
    const match = [...text.matchAll(/^data:(.+)$/gm)].at(-1)?.[1]
    let message = `对话请求失败（HTTP ${response.status}）`
    let code = response.status
    if (match) {
      try {
        const payload = JSON.parse(match) as ApiResponse<unknown>
        message = payload.message || message
        code = payload.code
      } catch {
        // Keep the normalized HTTP error.
      }
    }
    if (response.status === 401) window.dispatchEvent(new CustomEvent('csagent:auth-expired'))
    throw new ApiError(message, response.status, code)
  }
  if (!response.body) throw new ApiError('浏览器不支持流式响应', 0)

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let completed = false
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    let separator = buffer.indexOf('\n\n')
    while (separator >= 0) {
      const block = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      const parsed = parseSseBlock(block)
      if (parsed) {
        onEvent(parsed.event, parsed.data)
        if (parsed.event === 'done') completed = true
      }
      separator = buffer.indexOf('\n\n')
    }
    if (done) break
  }
  if (!completed && !signal?.aborted) {
    throw new ApiError('流式响应意外中断，请重试', 0)
  }
}

function parseSseBlock(block: string): { event: string; data: Record<string, unknown> } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return null
  const raw = dataLines.join('\n')
  try {
    return { event, data: JSON.parse(raw) as Record<string, unknown> }
  } catch {
    return { event, data: { text: raw } }
  }
}

export type RealtimeState = 'connecting' | 'live' | 'reconnecting' | 'offline' | 'closed'

export class RealtimeClient {
  private socket: WebSocket | null = null
  private closed = false
  private reconnectAttempt = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private readonly cursorKey: string
  private lastId: number
  private seen = new Set<number>()

  constructor(
    username: string,
    private readonly onEvent: (event: RealtimeEnvelope) => void,
    private readonly onState: (state: RealtimeState) => void
  ) {
    this.cursorKey = `csagent:event-cursor:${username}`
    this.lastId = Number(sessionStorage.getItem(this.cursorKey) || 0)
  }

  connect() {
    if (this.closed || this.socket?.readyState === WebSocket.OPEN) return
    this.onState(this.reconnectAttempt ? 'reconnecting' : 'connecting')
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const socket = new WebSocket(`${protocol}://${location.host}/ws/events`)
    this.socket = socket
    socket.onopen = () => {
      this.reconnectAttempt = 0
      this.onState('live')
      const replayFrom = this.lastId
      void this.replay(replayFrom)
      this.pingTimer = setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'ping' }))
      }, 25_000)
    }
    socket.onmessage = (message) => {
      try {
        this.deliver(JSON.parse(message.data) as RealtimeEnvelope)
      } catch {
        // Invalid frames are ignored; persisted replay remains authoritative.
      }
    }
    socket.onerror = () => socket.close()
    socket.onclose = () => {
      this.stopPing()
      if (this.closed) {
        this.onState('closed')
        return
      }
      this.onState(navigator.onLine ? 'reconnecting' : 'offline')
      this.scheduleReconnect()
    }
  }

  close() {
    this.closed = true
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    this.stopPing()
    this.socket?.close()
    this.socket = null
    this.onState('closed')
  }

  private async replay(afterId: number) {
    let cursor = afterId
    for (let page = 0; page < 5; page += 1) {
      try {
        const events = await api.get<RealtimeEnvelope[]>(`/api/events?afterId=${cursor}&limit=200`, {
          retry: 2,
          dedupe: false
        })
        for (const event of events) {
          this.deliver(event)
          cursor = Math.max(cursor, event.id)
        }
        if (events.length < 200) return
      } catch {
        return
      }
    }
  }

  private deliver(event: RealtimeEnvelope) {
    if (event.id > 0) {
      if (this.seen.has(event.id)) return
      this.seen.add(event.id)
      if (this.seen.size > 500) this.seen = new Set([...this.seen].slice(-250))
      this.lastId = Math.max(this.lastId, event.id)
      sessionStorage.setItem(this.cursorKey, String(this.lastId))
    }
    this.onEvent(event)
  }

  private scheduleReconnect() {
    const delayMs = Math.min(15_000, 700 * 2 ** this.reconnectAttempt) + Math.random() * 250
    this.reconnectAttempt += 1
    this.reconnectTimer = setTimeout(() => this.connect(), delayMs)
  }

  private stopPing() {
    if (this.pingTimer) clearInterval(this.pingTimer)
    this.pingTimer = null
  }
}

function delay(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer)
        reject(new DOMException('Aborted', 'AbortError'))
      },
      { once: true }
    )
  })
}
