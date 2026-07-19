# AI Service Agent · 十维审计报告

> **审计日期**：2026-07-06（Round-6 复测 · project-hub-2）  
> **范围**：`ai-service-agent`（智答 · Spring Boot 3.5 · Vue 3 · Mock LLM）  
> **评分**：1–10 分  
> **关联**：[PRODUCTION-READINESS.md](../../ai-portfolio/PRODUCTION-READINESS.md)

---

## 总览

| 维度 | 得分 | 等级 |
| --- | ---: | --- |
| 1. 文档与 README | **8** | 良好 |
| 2. Docker 与部署 | **8** | 良好 |
| 3. CI / CD | **9** | 优秀 |
| 4. 性能与压测 | **8** | 良好（Hub :18084 k6 + SSE soak 实测 ✓） |
| 5. 安全基线 | **9** | 优秀（RBAC Phase 1 代码骨架 ✓） |
| 6. 测试与质量 | **8** | 良好 |
| 7. API 与架构 | **9** | 优秀 |
| 8. 前端 UX | **9** | 优秀 |
| 9. 演示与作品集 | **9** | 优秀 |
| 10. 可维护性与工程化 | **8** | 良好 |
| **加权平均** | **8.5** | **作品集就绪** |

**结论**：**Function Calling + 工单状态机 + Mock 零密钥**垂直 Agent 标杆；P6 Mock 四步向导与坐席 UI 适合对外演示。**Round-7**：RBAC Phase 1 代码骨架落地（`Role`/`Permission` 枚举、`SecurityConfig` 路由守卫、JWT `/api/auth/login`、`SecurityRbacTest` 401/403 矩阵 · project-hub-2）。

---

## 1. 文档与 README（8/10）

### 现状

- README：Mock 演示、Docker、6 工具说明、截图引用。
- `docs/USAGE.md`、`DEPLOYMENT.md`、`SECURITY.md`、`PERFORMANCE_REPORT.md`。
- CSDN 长文就绪；Hub 18082/18083。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | 确认 `docs/screenshots/*.png` 已提交（避免 README 破图） |
| P2 | Agent 编排 sequenceDiagram |
| P3 | 英文 README 与中文对齐 |

---

## 2. Docker 与部署（8/10）

### 现状

- MySQL + backend + frontend；`FRONTEND_HOST_PORT` / `BACKEND_HOST_PORT` 可覆盖。
- 默认 `LLM_PROVIDER=mock`，零密钥全链路。
- **API gzip + Cache-Control**（P6）。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P3 | 生产 Nginx 模板与 WebSocket 坐席推送说明 |
| P3 | HTTPS 实采 |

---

## 3. CI / CD（9/10）

### 现状

- GHA CI **绿** + badge；**backend · frontend · docker-smoke** 三 job 矩阵。
- `docker-smoke`：compose config → up → `/api/health` mock 探针 + 前端 `:18085` → down。
- `mvn verify` 15 cases；前端 build + audit 0。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | k6 smoke 纳入 CI optional job |
| P3 | docker-smoke 后追加 Playwright 快捷问题 E2E |

---

## 4. 性能与压测（8/10）

### 现状

- `performance/k6-smoke.js`；Hub `:18084` 只读 P95 **294.7 ms**（Round-6 复跑 · 504 iter · 0% 失败 · project-hub-2）。
- `performance/k6-sse-chat-soak.js`：5 VU × 30s · P95 **1.22 s** · 0% 失败（Mock SSE · checks 248/248 · Round-6 · project-hub-2）。
- CI `docker-smoke` 含 k6 只读 + SSE soak。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| ~~**P1**~~ | ~~跑 k6 写入 PERFORMANCE_REPORT 结果表~~ ✅ Round-6 |
| ~~**P2**~~ | ~~SSE chat Mock soak~~ ✅ |
| **P2** | local :8081 k6 填入 PERFORMANCE_REPORT（pending-local） |
| P3 | WebSocket 坐席广播延迟专项 |

---

## 5. 安全基线（8/10）

### 现状

- `SECURITY.md` 基础策略。
- Mock 环境无真实 PII；工单/订单为演示数据。
- **Round-7**：[RBAC-ROADMAP.md](./RBAC-ROADMAP.md) Phase 1 代码 — `Role`/`Permission` 枚举、`SecurityConfig` + JWT 过滤器、`/api/auth/login`、WebSocket 握手拦截；默认 `rbac-enabled=false` 保持 Hub Mock 零登录；`SecurityRbacTest` 5 cases 401/403 矩阵通过。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| ~~**P2**~~ | ~~应用层认证 + RBAC（Roadmap 单租户内角色）~~ ✅ Phase 1 文档 |
| ~~**P1**~~ | ~~Phase 1 实现：`spring-security` + JWT + 401/403 单测~~ ✅ Round-7 · project-hub-2 |
| P2 | API 限流与 OpenTelemetry |
| P3 | 生产密钥管理与审计日志 |

---

## 6. 测试与质量（8/10）

### 现状

- 意图/情绪/工单状态机/Mock 工具路由/上下文加载。
- 15 tests 通过。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | 前端组件单测（tool_call 展示） |
| P3 | Playwright：快捷问题「查订单123」E2E |

---

## 7. API 与架构（9/10）

### 现状

- 6 Function：`query_order`、`query_logistics`、`query_policy`、`create_ticket`、`query_ticket`、`reschedule_appointment`。
- `AgentOrchestrator` + SSE 事件（tool_call/tool_result/token/done）。
- 工单状态机 CAS + 审计事件。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P3 | 真 LLM 切换时的 fallback 与超时策略文档 |
| P3 | 多实例 WebSocket Redis 广播生产指南 |

---

## 8. 前端 UX（9/10）

### 现状（P6）

- **Mock 四步演示向导**。
- 智能对话 / 坐席工单 / 数据看板三页。
- 工具调用可视化 + 满意度组件；Playwright 已验 0 console errors。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 | 工单列表筛选/分页性能 |
| P3 | 深色模式、移动端坐席简化视图 |

---

## 9. 演示与作品集（9/10）

### 现状

- Docker 一键；「帮我查订单123」闭环。
- Hub verify；截图路径 README 已引用。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P1 | 截图文件入库或更新为 SVG/占位策略 |
| P2 | `demo-mock.ps1` 一键 |
| P3 | GIF 录屏 |

---

## 10. 可维护性与工程化（8/10）

### 现状

- Vite 8 / TS 6 升级；CHANGELOG / VERSION。
- 端口可覆盖与 portfolio 矩阵一致。

### 优化点

| 优先级 | 动作 |
| ---: | --- |
| P2 |  monorepo `test-all` 脚本 |
| P3 | OpenAPI 生成 |

---

## 优先行动清单（Top 6）

| # | 优先级 | 动作 | 维度 |
| ---: | ---: | --- | --- |
| 1 | ~~**P1**~~ | ~~k6 smoke 实测回填~~ ✅ Round-6 · 下一项：:8081 pending-local | 性能 8→9 |
| 2 | ~~**P1**~~ | ~~RBAC Phase 1 代码实现 + SecurityRbacTest~~ ✅ Round-7 | 安全 8→9 |
| 3 | **P1** | 截图文件入库 | 演示 |
| 4 | ~~**P2**~~ | ~~SSE chat soak~~ ✅ | 性能 |
| 5 | ~~**P2**~~ | ~~RBAC Roadmap 首期~~ ✅ | 安全 |
| 6 | **P3** | Playwright E2E | 测试 |

---

## 矩阵对照

全维 ✓ · 多租户 N/A · Hub ✓

---

## 相关文档

- [PERFORMANCE_REPORT.md](../PERFORMANCE_REPORT.md)
- [docs/RBAC-ROADMAP.md](./RBAC-ROADMAP.md)
- [docs/USAGE.md](./USAGE.md)
- [DEPLOYMENT.md](../DEPLOYMENT.md)

*Round-7 均分 **8.5**（D5 8→9）· :8081 k6 pending-local。*
