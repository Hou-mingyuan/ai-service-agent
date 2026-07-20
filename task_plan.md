# AI Service Agent 完成态计划

## Goal

严格执行统一完成标准和“4. AI Service Agent”，仅修改本仓库；在全部验收项有证据、零已知严重缺陷后完成 Goal。服务端口限定为 `19040–19049`，不提交、不推送、不发布。

## 完成标准

- 客户、知识、Agent 工具、意图/情绪、自动转人工、坐席双向接管、工单/SLA、RBAC、审计、实时事件和看板均形成真实闭环。
- Mock 与真实 adapter 共用契约且边界显式；敏感操作必须确认、幂等、可审计。
- 后端、前端、并发、安全、E2E、Compose/MySQL、性能、secret、差异和开箱启动门禁全部通过。
- 真实浏览器完成客户工具调用到坐席接管、工单关闭和满意度的同链路 E2E。

## 阶段

| 阶段 | 状态 | 交付 |
| --- | --- | --- |
| 1. 基线审计与能力矩阵 | completed | 完整读取规则/代码/文档，运行旧链路，记录真实缺口并冻结边界 |
| 2. 后端业务与安全闭环 | completed | RBAC、知识、工具/adapter、敏感确认、人工接管、工单/SLA、审计、实时、看板 |
| 3. 前端体验与运营页面 | completed | 登录、客户、坐席、工单、知识、审计、看板、完整状态和响应式设计 |
| 4. 测试、性能与开箱启动 | completed | 40 个后端测试、8 个前端测试、38 条评估、k6、Lighthouse、Compose/MySQL |
| 5. 全量验收与缺陷收敛 | completed | 统一 7 项、专项 10 项与全部门禁均有证据；零已知严重或核心可复现 P2 缺陷 |

## 最终门禁清单

- [x] 后端 `mvn test`：40/40。
- [x] 前端 `npm run check`：lint/typecheck/Vitest 8/8/build。
- [x] Agent 评估：38/38，准确率和转人工召回 100%。
- [x] 全新 Compose/MySQL schema、seed、自动验收和重启持久化。
- [x] 默认持久化配置下读/写/SSE 性能预算。
- [x] 三类视口与 Lighthouse。
- [x] 真实浏览器同链路 E2E：会话 #7、工单 `TK20260720-33A16047`。
- [x] 验收证据、性能报告和完成态维度审计。
- [x] quality gate / secret scan / legacy endpoint scan：219 个文本文件通过。
- [x] `git diff --check`、最终端口/日志/产物/Git 范围核对。
- [x] 最终开箱启动复验：Compose/MySQL 与原生 H2/Vite 均 PASS。
- [x] 最终完成审计；本轮结尾同步 Goal `complete` 状态。

## 约束

- 只修改 `D:\project-hub\ai-service-agent`。
- 保留 Spring Boot + Vue 单体、H2/MySQL 与 MyBatis-Plus，不做无关重构。
- WebSocket 只承载可重放事件，业务写入走鉴权且幂等的 REST。
- 未获得真实外部凭据，不伪造真实 LLM 或业务系统质量通过。
