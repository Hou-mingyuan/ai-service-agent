/** Mock 零密钥演示向导：步骤定义与 localStorage 进度 */

export interface DemoStep {
  id: string
  title: string
  subtitle: string
  /** 自动填入并发送的示例话术 */
  prompt?: string
  /** 跳转路由（hash 路由不含 #） */
  route?: string
  /** 完成判定：sent=发送过该话术；route=访问过该页；manual=用户点完成 */
  doneWhen: 'sent' | 'route' | 'manual'
}

export const DEMO_STEPS: DemoStep[] = [
  {
    id: 'welcome',
    title: '欢迎使用 Mock 演示',
    subtitle: '当前为内置 Mock 模型，无需 API Key。按步骤体验查单、投诉转人工与坐席工单闭环。',
    doneWhen: 'manual'
  },
  {
    id: 'order',
    title: '① 查询订单',
    subtitle: '点击「发送示例」体验 Function Calling：query_order 工具会返回真实 Mock 订单数据。',
    prompt: '帮我查订单123',
    route: '/chat',
    doneWhen: 'sent'
  },
  {
    id: 'logistics',
    title: '② 查询物流',
    subtitle: '继续体验 query_logistics，观察 SSE 流式输出与工具调用可视化。',
    prompt: '订单123的快递到哪了',
    route: '/chat',
    doneWhen: 'sent'
  },
  {
    id: 'complaint',
    title: '③ 投诉转人工',
    subtitle: '负面情绪触发自动升级：create_ticket + handoff 事件，坐席端 WebSocket 即时推送。',
    prompt: '你们太差了，我要投诉！',
    route: '/chat',
    doneWhen: 'sent'
  },
  {
    id: 'tickets',
    title: '④ 坐席处理工单',
    subtitle: '切换到「坐席工单」，查看刚创建的工单、指派处理人并完成状态流转。',
    route: '/tickets',
    doneWhen: 'route'
  }
]

const STORAGE_KEY = 'csagent-demo-progress'

export interface DemoProgress {
  completed: string[]
  dismissed: boolean
}

export function loadDemoProgress(): DemoProgress {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch {
    /* ignore */
  }
  return { completed: [], dismissed: false }
}

export function saveDemoProgress(p: DemoProgress) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(p))
}

export function markStepDone(id: string) {
  const p = loadDemoProgress()
  if (!p.completed.includes(id)) {
    p.completed.push(id)
    saveDemoProgress(p)
  }
}

export function dismissDemoWizard() {
  const p = loadDemoProgress()
  p.dismissed = true
  saveDemoProgress(p)
}

export function resetDemoWizard() {
  localStorage.removeItem(STORAGE_KEY)
}
