# 智答 AI Service Agent 性能报告

报告日期：2026-07-06

## 目标

| 指标 | 阈值 | 范围 |
| --- | --- | --- |
| 错误率 | `< 1%` | `/api/health`、`/api/dashboard/overview` |
| P95 延迟 | `< 800ms` | 同上（只读 API） |
| 并发 | 10–50 VU | k6 `constant-vus` smoke |

SSE 对话与 LLM 工具调用链路单独评估，**不纳入**本 smoke 阈值。

## 压测脚本

脚本：`performance/k6-smoke.js`

覆盖端点：

- `GET /api/health`
- `GET /api/dashboard/overview`

### 前置条件

1. 后端已启动（推荐 `docker compose up -d --build`，Mock 零密钥即可）
2. 本机已安装 Docker（用于运行 k6 容器）
3. 默认后端端口：**8081**（`.env` 中 `BACKEND_HOST_PORT` 可覆盖）

### 运行示例

**Linux / macOS：**

```bash
cd ai-service-agent
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8081 \
  -e VUS=20 \
  -e DURATION=1m \
  -v "$(pwd)/performance:/scripts" \
  grafana/k6:latest run /scripts/k6-smoke.js
```

**Windows PowerShell：**

```powershell
cd ai-service-agent
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8081 `
  -e VUS=20 `
  -e DURATION=1m `
  -v D:/project-hub/ai-service-agent/performance:/scripts `
  grafana/k6:latest run /scripts/k6-smoke.js
```

可选环境变量：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `BASE_URL` | `http://127.0.0.1:8081` | 后端根 URL |
| `VUS` | `10` | 并发虚拟用户 |
| `DURATION` | `1m` | 持续时间 |
| `THINK_TIME_SECONDS` | `1` | 每轮请求间隔 |

### 结果解读

k6 结束时检查：

```
✓ http_req_failed........: rate<0.01
✓ http_req_duration{type:fast}...: p(95)<800ms
```

若失败，先确认 `curl -f http://127.0.0.1:8081/api/health` 与 MySQL 容器 healthy。

## 当前状态

- 后端 JUnit 覆盖意图/情绪/工单状态机与 Mock 工具路由全链路（`mvn test`）。
- 前端 Vite 生产构建可用（`npm run build`）。
- Docker Compose 默认 `LLM_PROVIDER=mock`，零密钥可完整演示。
- k6 smoke 脚本已就绪，阈值与 [DEPLOYMENT.md](DEPLOYMENT.md) §6 一致。

## k6 实测（2026-07-06）

| 环境 | BASE | VU × 时长 | P95 (fast) | 失败率 | 备注 |
| --- | --- | --- | --- | --- | --- |
| Hub Profile (Round-6) | `:18084` | 20 × 30s | **294.7 ms** | **0%** | project-hub-2 复跑 · 504 iter · 阈值全过 |
| Hub Profile | `:18084` | 20 × 30s | **324 ms** | **0%** | health + dashboard overview |
| local docker | `:8081` | — | _pending-local_ | — | `docker compose up` 后同上命令 |

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:18084 `
  -e VUS=20 -e DURATION=30s `
  -v D:/project-hub/ai-service-agent/performance:/scripts `
  grafana/k6:latest run /scripts/k6-smoke.js
```

### 截图归档（pending-local）

`_optimization-screenshots/ai-service-agent/` 待 Playwright/UI 脚本出图（需 `:18085` 前端栈运行）。

## SSE Chat Soak（2026-07-06）

脚本：`performance/k6-sse-chat-soak.js`

覆盖：`POST /api/chat`（Mock LLM · SSE 流式），断言 `event:start` + `event:done`。

| 环境 | BASE | VU × 时长 | P95 (sse) | 失败率 | 迭代 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| Hub Profile (Round-6) | `:18084` | 5 × 30s | **1.22 s** | **0%** | 62 | project-hub-2 复跑 · checks 248/248 |
| Hub Profile | `:18084` | 5 × 30s | **2.6 s** | **0%** | 77 | checks 308/308 |
| local docker | `:8081` | — | _pending-local_ | — | — | 同上命令 |

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:18084 `
  -e VUS=5 -e DURATION=30s `
  -v D:/project-hub/ai-service-agent/performance:/scripts `
  grafana/k6:latest run /scripts/k6-sse-chat-soak.js
```

阈值：`http_req_duration{type:sse} p(95)<10s` · `checks>98%`（不纳入只读 smoke 的 800ms 阈值）。

## RBAC Roadmap（P2）

应用层认证与单租户角色规划见 [docs/RBAC-ROADMAP.md](docs/RBAC-ROADMAP.md)（Phase 1 鉴权骨架 · 权限矩阵 · 验收命令）。

## 后续优化

- [x] 对 `/api/chat` SSE 端点增加独立 soak 测试（含 Mock LLM）→ `performance/k6-sse-chat-soak.js`
- [x] 将 k6 smoke + SSE soak 纳入 CI（`docker-smoke` job）
- [ ] 生产环境在网关层增加分布式限流与 OpenTelemetry 指标
- [ ] 坐席 WebSocket 连接数与广播延迟专项压测
