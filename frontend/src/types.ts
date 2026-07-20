export type UserRole = 'customer' | 'agent' | 'supervisor' | 'admin'

export interface SessionUser {
  username: string
  displayName: string
  role: UserRole
  permissions: string[]
  expiresInMinutes: number
}

export interface LoginResponse extends SessionUser {
  accessToken: string | null
  tokenType: string
}

export interface HealthInfo {
  status: string
  mode: 'DEMO' | 'STANDARD'
  llm: { provider: string; mock: boolean }
  business: { source: string; status: string; mock: boolean; detail: string }
  knowledge: { source: string; mock: boolean }
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export type ConversationStatus = 'BOT' | 'HUMAN_PENDING' | 'HUMAN' | 'CLOSED'

export interface Conversation {
  id: number
  tenantId: string
  sessionKey: string
  customerUsername: string
  userName: string
  channel: string
  status: ConversationStatus
  botEnabled: number
  assignedAgent: string | null
  handoffReason: string | null
  lastIntent: string | null
  lastSentiment: string | null
  resolved: number
  handoffAt: string | null
  claimedAt: string | null
  firstResponseAt: string | null
  lastMessageAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface ConversationMessage {
  id: number
  tenantId: string
  conversationId: number
  clientMessageId: string | null
  role: 'user' | 'assistant' | 'agent' | 'tool' | 'system'
  senderUsername: string | null
  senderName: string | null
  content: string
  deliveryStatus: string
  readAt: string | null
  intent: string | null
  intentConfidence: number | null
  sentiment: string | null
  sentimentScore: number | null
  classificationSource: string | null
  toolName: string | null
  toolArgs: string | null
  toolResult: string | null
  createdAt: string
}

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'PENDING' | 'RESOLVED' | 'CLOSED'
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface Ticket {
  id: number
  tenantId: string
  ticketNo: string
  idempotencyKey: string | null
  conversationId: number | null
  category: string
  title: string
  description: string | null
  priority: TicketPriority
  status: TicketStatus
  assignee: string | null
  source: string
  customer: string | null
  resolutionNote: string | null
  closeReason: string | null
  slaDueAt: string | null
  slaWarningAt: string | null
  slaBreachedAt: string | null
  escalatedAt: string | null
  version: number
  createdAt: string
  updatedAt: string
  closedAt: string | null
}

export interface TicketEvent {
  id: number
  ticketId: number
  eventType: string
  eventKey: string | null
  fromStatus: TicketStatus | null
  toStatus: TicketStatus | null
  note: string | null
  operator: string | null
  actorRole: string | null
  metadataJson: string | null
  createdAt: string
}

export interface TicketDetails {
  ticket: Ticket
  events: TicketEvent[]
}

export interface ToolExecution {
  id: number
  conversationId: number
  customerUsername: string
  toolName: string
  argumentsJson: string
  resultJson: string | null
  summary: string | null
  status: string
  sensitive: number
  adapterSource: string | null
  confirmedBy: string | null
  confirmedAt: string | null
  durationMs: number | null
  errorCode: string | null
  createdAt: string
  updatedAt: string
}

export interface ToolResult {
  ok: boolean
  summary: string
  data: unknown
  status: string
  executionId: number | null
  dataSource: string
  replayed: boolean
}

export interface KnowledgeDocument {
  id: number
  title: string
  sourceUri: string | null
  content: string
  checksum: string
  status: 'READY' | 'ARCHIVED'
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface KnowledgeSearchResult {
  citation: string
  documentId: number
  chunkId: number
  title: string
  sourceUri: string | null
  excerpt: string
  score: number
  dataSource: string
}

export interface AuditLog {
  id: number
  requestId: string
  actorUsername: string
  actorRole: string
  action: string
  resourceType: string
  resourceId: string | null
  outcome: string
  idempotencyKey: string | null
  detailsJson: string | null
  createdAt: string
}

export interface DashboardData {
  range: { from: string; to: string; timezone: string }
  kpis: {
    conversations: number
    avgFirstResponseSeconds: number
    resolutionRate: number
    handoffRate: number
    openTickets: number
    ticketResolutionRate: number
    slaBreaches: number
    satisfaction: number
  }
  counts: { messages: number; toolCalls: number; feedback: number; tickets: number }
  tickets: {
    byStatus: Record<string, number>
    byCategory: Record<string, number>
    byPriority: Record<string, number>
  }
  trend: Array<{
    date: string
    conversations: number
    handoffs: number
    closedTickets: number
    slaBreaches: number
  }>
  definitions: Record<string, string>
  llmProvider: string
}

export interface RealtimeEnvelope<T = unknown> {
  id: number
  type: string
  createdAt: string
  payload: T
}

export interface OrderData {
  orderNo: string
  product: string
  amount: number
  status: string
  maskedAddress: string
  dataSource: string
}

export interface PolicyData {
  policyNo: string
  maskedHolder: string
  product: string
  premium: number
  status: string
  effectiveDate: string
  expireDate: string
  nextPaymentDate: string
  dataSource: string
}
