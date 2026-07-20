const baseUrl = (process.env.BASE_URL || 'http://127.0.0.1:19041').replace(/\/$/, '')
const runId = `accept-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`

function check(condition, message) {
  if (!condition) throw new Error(message)
}

async function api(path, { token, method = 'GET', body, expected = 200 } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15_000)
  })
  const text = await response.text()
  let payload
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    throw new Error(`${method} ${path} returned non-JSON: ${text.slice(0, 160)}`)
  }
  check(response.status === expected,
    `${method} ${path} expected ${expected}, got ${response.status}: ${payload?.message || text}`)
  return payload
}

async function login(username, password) {
  const result = await api('/api/auth/login', { method: 'POST', body: { username, password } })
  check(result?.data?.accessToken, `${username} login did not return an access token`)
  return result.data.accessToken
}

function parseSse(text) {
  return text.split(/\r?\n\r?\n/).filter(Boolean).map((block) => {
    const lines = block.split(/\r?\n/)
    const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
    const dataText = lines.filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim()).join('\n')
    let data = dataText
    try { data = JSON.parse(dataText) } catch { /* keep text for diagnostics */ }
    return { event, data }
  })
}

async function chat(token, message, suffix) {
  const response = await fetch(`${baseUrl}/api/chat`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ clientMessageId: `${runId}-${suffix}`, message }),
    signal: AbortSignal.timeout(30_000)
  })
  const text = await response.text()
  check(response.status === 200, `chat ${suffix} failed with HTTP ${response.status}: ${text.slice(0, 240)}`)
  const events = parseSse(text)
  check(events.some((item) => item.event === 'done'), `chat ${suffix} did not complete`)
  check(!events.some((item) => item.event === 'error'), `chat ${suffix} emitted an error`)
  return events
}

async function main() {
  const health = await api('/api/health')
  check(health?.data?.status === 'UP', 'backend is not UP')
  check(health?.data?.business?.source, 'health response does not identify the business adapter')

  const [customer, agent] = await Promise.all([
    login('customer', 'customer123'),
    login('agent', 'agent123')
  ])

  await api('/api/tickets', { token: customer, expected: 403 })

  const orderEvents = await chat(customer, '帮我查订单123', 'order')
  const orderTool = orderEvents.find((item) => item.event === 'tool_result')?.data
  check(orderTool?.name === 'query_order' && orderTool?.ok === true, 'order tool was not executed successfully')
  check(orderTool?.dataSource === health.data.business.source, 'tool result source does not match health metadata')

  const knowledgeEvents = await chat(customer, '退货需要满足什么条件？', 'knowledge')
  check(JSON.stringify(knowledgeEvents).includes('KB-1-1'), 'knowledge answer did not include source KB-1-1')

  const complaintEvents = await chat(customer, '你们太差了，我要投诉并转人工！', 'complaint')
  const start = complaintEvents.find((item) => item.event === 'start')?.data
  const handoff = complaintEvents.find((item) => item.event === 'handoff')?.data
  check(start?.conversationId && handoff?.ticketId && handoff?.ticketNo,
    'complaint did not create a handoff conversation and ticket')

  const queue = await api('/api/conversations?mode=queue&page=1&size=100', { token: agent })
  check(queue.data.records.some((item) => item.id === start.conversationId), 'handoff is absent from the agent queue')

  const claim = await api(`/api/conversations/${start.conversationId}/claim`, {
    token: agent,
    method: 'POST'
  })
  check(claim.data.conversation.assignedAgent === 'agent', 'agent did not claim the conversation')
  check(claim.data.ticket.id === handoff.ticketId && claim.data.ticket.status === 'IN_PROGRESS',
    'claim did not atomically accept the linked ticket')

  const replyBody = {
    clientMessageId: `${runId}-agent-reply`,
    content: '您好，我已接管并核查您的问题，现在为您完成处理。'
  }
  const reply = await api(`/api/conversations/${start.conversationId}/messages`, {
    token: agent,
    method: 'POST',
    body: replyBody
  })
  const replay = await api(`/api/conversations/${start.conversationId}/messages`, {
    token: agent,
    method: 'POST',
    body: replyBody
  })
  check(reply.data.replayed === false && replay.data.replayed === true,
    'agent message idempotency contract failed')

  const customerMessages = await api(`/api/conversations/${start.conversationId}/messages`, { token: customer })
  check(customerMessages.data.some((item) => item.content === replyBody.content),
    'customer cannot read the agent reply')

  const resolved = await api(`/api/tickets/${handoff.ticketId}/transition`, {
    token: agent,
    method: 'POST',
    body: { toStatus: 'RESOLVED', note: '自动验收：坐席已解决' }
  })
  check(resolved.data.status === 'RESOLVED', 'ticket did not reach RESOLVED')

  await api('/api/feedback', {
    token: customer,
    method: 'POST',
    body: { conversationId: start.conversationId, rating: 5, comment: '自动验收通过' }
  })
  const [closedConversation, closedTicket] = await Promise.all([
    api(`/api/conversations/${start.conversationId}`, { token: customer }),
    api(`/api/tickets/${handoff.ticketId}`, { token: agent })
  ])
  check(closedConversation.data.status === 'CLOSED' && closedConversation.data.resolved === 1,
    'feedback did not close the conversation')
  check(closedTicket.data.ticket.status === 'CLOSED', 'feedback did not close the linked ticket')

  console.log(JSON.stringify({
    status: 'PASS',
    baseUrl,
    mode: health.data.mode,
    businessSource: health.data.business.source,
    conversationId: start.conversationId,
    ticketNo: handoff.ticketNo,
    checks: [
      'health-and-source', 'rbac-403', 'order-tool', 'knowledge-citation',
      'automatic-handoff', 'agent-claim', 'bidirectional-message',
      'message-idempotency', 'ticket-resolved', 'feedback-closed'
    ]
  }, null, 2))
}

main().catch((error) => {
  console.error(`ACCEPTANCE FAILED: ${error.message}`)
  process.exitCode = 1
})
