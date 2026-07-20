import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:19040'
const VUS = Number(__ENV.VUS || 3)
const DURATION = __ENV.DURATION || '10s'
const THINK = Number(__ENV.THINK_TIME_SECONDS || 0.2)
const RUN_ID = __ENV.RUN_ID || String(Date.now())

export const options = {
  scenarios: {
    write_smoke: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '15s'
    }
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{type:write}': ['p(95)<800']
  }
}

export function setup() {
  const response = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    username: 'agent',
    password: 'agent123'
  }), { headers: { 'Content-Type': 'application/json' } })
  check(response, { 'agent login ok': (value) => value.status === 200 && Boolean(value.json('data.accessToken')) })
  if (response.status !== 200) throw new Error(`agent login failed: ${response.status}`)
  return { token: response.json('data.accessToken') }
}

export default function (data) {
  const key = `k6-write-${RUN_ID}-${__VU}-${__ITER}`
  const response = http.post(`${BASE}/api/tickets`, JSON.stringify({
    category: 'OTHER',
    title: `k6 core write ${key}`,
    description: '可删除的本地性能测试工单',
    priority: 'LOW'
  }), {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
      'Idempotency-Key': key
    },
    tags: { type: 'write' }
  })
  check(response, {
    'ticket create 200': (value) => value.status === 200,
    'ticket created once': (value) => value.json('data.replayed') === false && Boolean(value.json('data.ticket.id'))
  })
  sleep(THINK)
}
