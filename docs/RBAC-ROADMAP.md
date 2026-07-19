# RBAC Roadmap · 智答 AI Service Agent

> **版本**：2026-07-06（P2 首期规划 · Round-6 定稿）  
> **范围**：单租户内角色与权限；面向作品集演示 → 准生产演进  
> **关联**：[SECURITY.md](../SECURITY.md) · [DIMENSION-AUDIT.md](./DIMENSION-AUDIT.md)

---

## 现状

| 能力 | 状态 |
| --- | --- |
| 用户端 `/api/chat` | 匿名可访问（演示友好） |
| 坐席 `/api/tickets/**` | 匿名可访问 |
| 运营看板 `/api/dashboard/**` | 匿名可访问 |
| WebSocket `/ws/agent` | 无鉴权 |
| 审计日志 | 工单状态变更有 DB 事件；无登录审计 |

当前设计适合 **Mock 零密钥 Hub 演示**，不适合多坐席生产环境。

---

## 目标角色（单租户）

| 角色 | 标识 | 典型使用者 | 核心权限 |
| --- | --- | --- | --- |
| **访客** | `visitor` | 终端用户（网页） | `chat:send`、`feedback:submit` |
| **坐席** | `agent` | 客服代表 | `ticket:read`、`ticket:transition`、`ticket:assign`、`ws:agent` |
| **主管** | `supervisor` | 班组长 | 坐席权限 + `ticket:escalate`、`dashboard:read` |
| **管理员** | `admin` | 运营/IT | 全部 API + `catalog:write`、`config:write` |

权限采用 **资源:动作** 字符串，便于 Spring `@PreAuthorize` 与前端菜单对齐。

---

## 分阶段交付

### Phase 1 — 鉴权骨架（P2 · Round-7 代码落地）

- [x] 引入 `spring-security` + JWT（或 Session Cookie）签发 `/api/auth/login`
- [x] 定义 `Role` 枚举与 `Permission` 常量表（见下节）
- [x] `/api/tickets/**`、`/api/dashboard/**` 要求 `agent` 及以上（`app.security.rbac-enabled=true` 时生效）
- [x] `/ws/agent` 握手校验 Bearer Token
- [x] `/api/chat` 保持匿名（或可选 `visitor` token 绑定会话）

**验收**：Postman 集合 + `mvn test` 覆盖 401/403 矩阵 ✅ `SecurityRbacTest` 5 cases。

### Phase 2 — 坐席工作流（P3）

- [ ] 坐席只能查看/操作已指派或队列内工单
- [ ] `supervisor` 可重新指派与升级 SLA
- [ ] 登录/登出/敏感操作写入 `audit_log` 表

### Phase 3 — 生产加固（P3+）

- [ ] API 网关层限流（按角色配额）
- [ ] OpenTelemetry：`auth.role`、`ticket.transition` 指标
- [ ] 密钥轮换与坐席账号锁定策略

---

## 权限矩阵（首期）

| 端点 / 资源 | visitor | agent | supervisor | admin |
| --- | :---: | :---: | :---: | :---: |
| `POST /api/chat` | ✓ | ✓ | ✓ | ✓ |
| `GET /api/tickets` | — | ✓ | ✓ | ✓ |
| `POST /api/tickets/{id}/transition` | — | ✓* | ✓ | ✓ |
| `POST /api/tickets/{id}/assign` | — | — | ✓ | ✓ |
| `GET /api/dashboard/overview` | — | — | ✓ | ✓ |
| `WS /ws/agent` | — | ✓ | ✓ | ✓ |
| `GET /api/catalog/*` | ✓ | ✓ | ✓ | ✓ |
| `POST /api/catalog/*` | — | — | — | ✓ |

\* `agent` 仅可操作已指派给自己的工单。

---

## 技术选型建议

| 组件 | 建议 | 说明 |
| --- | --- | --- |
| 认证 | JWT（HS256，15min access + refresh） | 与 portfolio 其他 Spring 项目一致 |
| 授权 | `@PreAuthorize("hasAuthority('ticket:read')")` | 方法级，易单测 |
| 前端 | Axios 拦截器注入 `Authorization` | 坐席页登录后持有 token |
| 演示兼容 | `app.security.anonymous-chat=true` 环境变量 | Hub Mock 可继续零登录演示 |

---

## 与 SECURITY.md 的关系

- **Out of scope（当前 release）** 条目将在 Phase 1 完成后移入 In scope。
- 生产部署前必须完成 Phase 1 + 网关 HTTPS/限流（见 [DEPLOYMENT.md](../DEPLOYMENT.md) §6）。

---

## 验收命令（Phase 1 完成后）

```bash
cd backend
mvn test -Dtest=SecurityRbacTest
curl -sf -H "Authorization: Bearer $AGENT_TOKEN" http://localhost:8081/api/tickets
```

*Phase 1 代码骨架 Round-7 落地（project-hub-2）；默认 `rbac-enabled=false` 保持 Hub Mock 演示。Phase 2+ 跟踪见 [README Roadmap](../README.md) 与 [OPTIMIZATION-BACKLOG](../../ai-portfolio/OPTIMIZATION-BACKLOG.md)。*
