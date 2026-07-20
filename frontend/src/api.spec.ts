import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

function response<T>(data: T, status = 200) {
  return new Response(JSON.stringify({ code: status >= 400 ? status : 0, message: status >= 400 ? '失败' : 'ok', data, requestId: 'req-test' }), {
    status, headers: { 'Content-Type': 'application/json' }
  })
}

describe('API client boundaries', () => {
  beforeEach(() => vi.unstubAllGlobals())

  it('deduplicates simultaneous GET requests', async () => {
    let resolveFetch: ((value: Response) => void) | undefined
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => { resolveFetch = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    const first = api.get<{ value: number }>('/api/example')
    const second = api.get<{ value: number }>('/api/example')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    resolveFetch?.(response({ value: 7 }))
    await expect(first).resolves.toEqual({ value: 7 })
    await expect(second).resolves.toEqual({ value: 7 })
  })

  it('adds CSRF and cookie credentials to mutations', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' }))
      .mockResolvedValueOnce(response({ saved: true }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(api.post('/api/items', { name: 'sample' })).resolves.toEqual({ saved: true })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    const mutation = fetchMock.mock.calls[1]
    const init = mutation[1] as RequestInit
    expect(init.credentials).toBe('include')
    expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('csrf-token')
  })

  it('refreshes a one-time CSRF token for sequential mutations', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-1' }))
      .mockResolvedValueOnce(response({ saved: 1 }))
      .mockResolvedValueOnce(response({ headerName: 'X-CSRF-TOKEN', token: 'csrf-2' }))
      .mockResolvedValueOnce(response({ saved: 2 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.post('/api/items', { value: 1 })
    await api.post('/api/items', { value: 2 })

    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(new Headers((fetchMock.mock.calls[1][1] as RequestInit).headers).get('X-CSRF-TOKEN')).toBe('csrf-1')
    expect(new Headers((fetchMock.mock.calls[3][1] as RequestInit).headers).get('X-CSRF-TOKEN')).toBe('csrf-2')
  })

  it('normalizes server failures with status and request ID', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(null, 403)))
    await expect(api.get('/api/forbidden', { retry: 0 })).rejects.toMatchObject({
      name: 'ApiError', status: 403, code: 403, requestId: 'req-test'
    })
  })
})
