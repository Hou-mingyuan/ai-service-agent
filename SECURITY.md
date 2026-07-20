# 安全策略

## 当前安全边界

- 默认开启后端 RBAC；前端菜单隐藏不是授权依据。
- 四类账号持久化在 `app_user`，密码使用 BCrypt（cost 12）。
- JWT 可通过 Bearer 或 `HttpOnly` Cookie 使用；Cookie 写请求强制 CSRF，默认 `SameSite=Strict`。
- 会话、消息、工单、工具执行单、实时事件和知识文档同时校验租户与资源归属；越权枚举返回 404。
- 请求具有 `X-Request-ID`；审计记录操作者、角色、动作、资源、结果、幂等键和时间。
- 密码、token、API key、地址等字段在审计明细中递归脱敏；业务查询返回脱敏姓名与地址。
- Prompt、知识片段和工具结果均按不可信数据处理；注入文本不能修改系统规则或泄露配置。
- 改期为敏感工具：模型只能创建待确认执行单，客户确认后才执行；确认有归属校验、有效期、原子状态更新、幂等与审计。
- WebSocket 在握手时验证 token，事件先按租户与受众过滤再重放。

## Mock 与真实模式

Demo 账号、订单、保单、地址、工单和截图均为虚构数据。健康接口与 UI 会明确标识 `mock` 数据源。

`APP_DEMO_ENABLED=false` 时，启动门禁禁止：

- Mock LLM 或 Mock 业务 adapter
- 匿名对话或关闭 RBAC
- 默认开发 JWT 密钥
- 通配 CORS
- 非 MySQL profile

真实 LLM 和 HTTP 业务 adapter 缺少密钥、地址或令牌时会启动失败，不会静默回退。LLM 私网 URL 默认被拒绝，降低 SSRF 风险。

## 角色摘要

| 角色 | 允许范围 |
| --- | --- |
| `customer` | 自己的对话/消息/评价/工具执行单；发起对话和确认敏感操作 |
| `agent` | 未认领队列、自己认领的会话与工单；双向回复和合法流转 |
| `supervisor` | 全部坐席会话/工单、指派、看板、知识只读、审计只读 |
| `admin` | 主管能力 + 客户能力 + 知识写入/归档与配置权限 |

完整矩阵见 [docs/RBAC-ROADMAP.md](docs/RBAC-ROADMAP.md)。

## 生产基线

1. 使用 HTTPS；将数据库和业务 adapter 放入私网，不公开 `19042`。
2. 从密钥管理系统注入数据库密码、JWT 密钥、LLM key 和业务 token；不要写入 `.env`、镜像或日志。
3. 设置 `APP_SECURITY_COOKIE_SECURE=true` 和精确 CORS origin。
4. 在 API 网关按身份/IP 做速率限制、请求体限制和异常流量防护。应用本身不替代边缘限流。
5. 定期轮换服务密钥、禁用离职坐席账号并审阅 `AUTH_*`、`TOOL_*`、`TICKET_*`、`KNOWLEDGE_*` 审计事件。
6. 发布前备份数据库，使用受审查的 schema 迁移；不要把 Demo 的建表初始化当成旧库迁移工具。

## 回归验证

```bash
cd backend
mvn test

cd ../frontend
npm audit --audit-level=high

cd ..
git diff --check
```

后端回归覆盖：401/403、客户与坐席 ID 枚举、Cookie CSRF、异常请求脱敏、敏感执行单归属、审计递归脱敏、Prompt 注入、并发认领、并发建单、SLA 幂等和事件受众隔离。

仓库 secret scan 应排除示例占位符与虚构 Demo 密码后检查高风险模式；完成证据见 [ACCEPTANCE_EVIDENCE.md](ACCEPTANCE_EVIDENCE.md)。

## 报告漏洞

不要在公开 issue 中提交可利用细节、真实凭据或个人数据。优先使用仓库的 GitHub Private Vulnerability Reporting / Security Advisory；若未启用，只公开请求私密联系方式。

报告请包含受影响版本、最小复现、影响、日志中的 request id，以及已脱敏的证据。

## 支持范围

安全修复以 `main` 最新版本为准。第三方 LLM 与业务系统自身的安全状况不由本仓库保证，但其连接、超时、认证、数据边界和失败行为属于本项目审查范围。
