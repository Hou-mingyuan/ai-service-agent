# RBAC 与资源隔离（已实现）

本文件保留原路径以兼容历史链接，但内容描述当前完成态，不是 Roadmap。授权以服务端权限和资源归属为准。

## 角色与能力

| 能力 | customer | agent | supervisor | admin |
| --- | :---: | :---: | :---: | :---: |
| 发起客户对话、提交评价 | ✓ | — | — | ✓ |
| 查看自己的会话/消息 | ✓ | — | — | ✓ |
| 查看人工队列 | — | ✓ | ✓ | ✓ |
| 查看已分配会话并双向回复 | — | ✓ | ✓ | ✓ |
| 查看工单 | — | 仅自己/队列 | 全部 | 全部 |
| 认领与合法流转 | — | ✓ | ✓ | ✓ |
| 指派/升级 | — | — | ✓ | ✓ |
| 查看工具执行记录 | 自己 | 可访问会话 | 全部 | 全部 |
| 确认敏感工具 | 仅自己的执行单 | — | — | ✓ |
| 知识读取 | — | — | ✓ | ✓ |
| 知识入库/归档 | — | — | — | ✓ |
| 看板 | — | — | ✓ | ✓ |
| 审计 | — | — | ✓ | ✓ |
| 实时事件 | 自己的受众 | 坐席受众 | 坐席受众 | 全部授权受众 |

权限字符串定义在 `Permission`：

```text
chat:send              feedback:submit       conversation:self
conversation:queue     conversation:all      message:reply
ticket:read            ticket:transition     ticket:assign
ticket:escalate        tool:read             tool:sensitive
knowledge:read         knowledge:write       dashboard:read
audit:read             ws:events             config:write
```

## 资源级规则

- 所有实体查询先限制 `tenant_id`。
- 客户会话还限制 `customer_username`；其他客户的 id 返回 404，避免确认资源存在。
- 普通坐席只能访问未认领队列或 `assigned_agent=当前用户名` 的会话与工单。
- 主管和管理员可查看同租户全部会话与工单；只有主管及以上能指派。
- 工具先校验角色权限，再由具体工具校验订单/保单/工单是否属于当前客户。
- 敏感执行单确认同时匹配 `tenant_id + id + customer_username + PENDING_CONFIRMATION`。
- WebSocket/事件重放先按租户、角色和 `audience_id` 过滤，再应用 limit，其他用户事件不能挤占重放窗口。

## 认证与 CSRF

- `/api/auth/login` 成功后同时返回短期 access token 并设置 `HttpOnly` Cookie。
- API 客户端可使用 `Authorization: Bearer <token>`；浏览器默认使用 Cookie。
- 使用认证 Cookie 的非安全方法必须先取 `/api/auth/csrf`，并发送对应 CSRF header；Bearer 请求不依赖 CSRF。
- `/api/auth/session` 可匿名探测并在未登录时返回空身份；`/api/auth/me` 始终要求认证。
- JWT 包含用户 id、租户、显示名和角色；服务端仍从持久化用户校验启用状态。

## HTTP 语义

| 情况 | 状态 |
| --- | ---: |
| 未登录访问受保护资源 | 401 |
| 已登录但缺权限 | 403 |
| 跨客户/跨坐席枚举资源 | 404 |
| 并发认领或非法状态竞争 | 409 |
| 参数/状态机校验失败 | 400/422 |

统一错误体和正常响应都包含 request id。前端有独立的未登录、权限不足、空数据、离线与错误状态。

## 回归证据

```bash
cd backend
mvn test -Dtest=SecurityRbacTest,SecurityBoundaryTest
```

覆盖四角色权限、匿名探测、客户会话 ID 枚举、坐席工单 ID 枚举、Cookie CSRF、SSE 畸形请求、异常媒体类型、敏感执行单归属和审计递归脱敏。完整结果见 [验收证据](../ACCEPTANCE_EVIDENCE.md)。
