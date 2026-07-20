# Changelog

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本。

## [Unreleased] - 2026-07-20

### Added

- `customer/agent/supervisor/admin` 持久化账号、后端 RBAC、Cookie/Bearer JWT、CSRF 和资源级隔离
- 文档入库、分块、租户检索、无答案策略、来源引用与管理员归档
- 工具 JSON Schema、权限、超时、幂等、执行单、adapter 来源与脱敏审计
- 敏感改期确认；确认前无副作用，确认后原子执行，重复确认重放
- 转人工队列、原子认领、客户/坐席双向消息、已读、机器人暂停/恢复
- 工单随机编号、乐观锁、关闭原因、重开、SLA 预警/超时升级和完整时间线
- 持久化 WebSocket 事件、受众隔离和 `afterId` 重放
- 真实事件看板、日期范围、指标口径和趋势
- 客户、坐席、工单、知识、审计、看板全套响应式页面与 Lucide 图标
- 38 条固定 Agent 评估集、并发/安全/adapter/取消回归、前端 Vitest
- Compose 健康等待、MySQL schema 门禁、自动验收脚本和 k6 CI

### Changed

- Mock 与真实 LLM/业务 adapter 使用同一契约；缺真实配置时启动失败，不再静默回退
- WebSocket 统一为 `/ws/events`；写操作保留在幂等 REST
- 默认开启 RBAC，默认时间与看板统一为 `Asia/Shanghai`
- 固定使用 `19040` 后端、`19041` 前端、`19042` MySQL
- 前端从演示卡片重构为可扫描的客服工作台，并补齐 loading/empty/error/offline/权限状态

### Fixed

- MySQL 8 保留字导致 `tool_execution` 未建表及查询别名错误；schema 失败现在阻止启动
- 异常媒体类型与防火墙拒绝请求返回脱敏 400，不再落成 500
- 已关闭会话刷新后重复显示评分入口
- 坐席端关闭事件与事务提交竞争导致状态不能实时刷新
- 同一 SSE 客户消息、敏感确认、工单创建和人工回复的重复副作用
- 容器 UTC 导致工单日期、SLA 和看板时区不一致

## [1.0.0] - 2026-07-04

### Added

- Spring Boot + Vue 客服 Agent 基线
- 6 个 Function Calling 工具、规则意图/情绪、SSE 对话、工单状态机、Mock LLM 与 Docker Compose

[Unreleased]: https://github.com/Hou-mingyuan/ai-service-agent/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Hou-mingyuan/ai-service-agent/releases/tag/v1.0.0
