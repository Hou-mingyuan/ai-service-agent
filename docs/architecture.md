# 架构设计说明

## 1. 总体架构

```mermaid
flowchart LR
    subgraph FE["前端 (Vue3 + Vite)"]
        Chat[智能对话]
        Console[坐席工单后台]
        Dash[数据看板]
    end
    subgraph BE["后端 (Spring Boot 3)"]
        API[REST + SSE 接口]
        WS[WebSocket 事件通道]
        ORCH[Agent 编排器]
        INTENT[意图识别]
        EMO[情绪识别]
        TOOLS[工具注册中心\nFunction Calling]
        TICKET[工单状态机]
        LLM[LLM 抽象层\nOpenAI 兼容 / Mock]
    end
    DB[(MySQL / H2\nMyBatis-Plus)]
    EXT[大模型网关\nOpenAI / DeepSeek / 通义 / Ollama]

    Chat -->|SSE| API --> ORCH
    Console -->|REST| API
    Console -.->|实时| WS
    Dash -->|REST| API
    ORCH --> INTENT
    ORCH --> EMO
    ORCH --> TOOLS
    ORCH --> LLM
    ORCH --> TICKET
    TOOLS --> DB
    TICKET --> DB
    TICKET -->|广播| WS
    ORCH --> DB
    LLM --> EXT
```

## 2. 一次对话的处理时序

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as 前端
    participant C as ChatController(SSE)
    participant O as AgentOrchestrator
    participant L as LlmClient
    participant T as 业务工具

    U->>F: 输入「帮我查订单123」
    F->>C: POST /api/chat (SSE)
    C->>O: handle(conversation, text)
    O->>O: 意图识别 / 情绪识别
    O-->>F: event: meta（意图/情绪）
    O->>L: chat(messages, tools) 规划
    L-->>O: tool_calls=[query_order(order_no=123)]
    O-->>F: event: tool_call
    O->>T: 执行 query_order
    T-->>O: 订单详情
    O-->>F: event: tool_result
    O->>L: chatStream(messages+工具结果)
    L-->>O: 逐 token
    O-->>F: event: token ... event: done
    F-->>U: 实时渲染答复
```

## 3. 转人工 / 自动升级

- 触发条件：用户显式「转人工」，或情绪识别为负面且负面强度 ≥ 阈值（默认 0.6）。
- 处理：自动创建工单（投诉类为 URGENT/HIGH），会话状态置为 `HUMAN_PENDING`，通过 WebSocket 广播 `ticket.created`，坐席后台实时收到。

## 4. 工单状态机

```mermaid
stateDiagram-v2
    [*] --> OPEN
    OPEN --> IN_PROGRESS
    OPEN --> PENDING
    OPEN --> RESOLVED
    OPEN --> CLOSED
    IN_PROGRESS --> PENDING
    IN_PROGRESS --> RESOLVED
    IN_PROGRESS --> CLOSED
    PENDING --> IN_PROGRESS
    PENDING --> RESOLVED
    PENDING --> CLOSED
    RESOLVED --> IN_PROGRESS: 重开
    RESOLVED --> CLOSED
    CLOSED --> [*]
```

所有流转都会写入 `ticket_event` 流转记录表，坐席后台以时间线形式展示。

## 5. LLM 可插拔设计

`LlmClient` 接口屏蔽供应方差异：

- `OpenAiLlmClient`：对接任意 OpenAI 兼容 `/chat/completions`，支持 Function Calling 与 SSE 流式。
- `MockLlmClient`：离线内置、规则驱动，无需密钥即可跑通「意图→工具→流式答复」全链路，用于本地体验、单测与 CI。

由 `LlmConfig` 依据 `app.llm.provider` 与是否配置密钥自动选择；未配置密钥时安全回退到 Mock。
