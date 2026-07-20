# AI Service Agent 进度日志

## 2026-07-20 最终收口

### 实现完成

- 后端：认证/RBAC、资源隔离、请求 ID、错误模型、脱敏审计、知识检索、Agent 防护、工具/adapter、敏感确认、人工接管、工单/SLA、实时事件、看板和生产门禁。
- 前端：登录、客户对话、坐席工作台、工单、知识、审计、看板及 loading/empty/error/disabled/success/offline/permission 状态；三类视口响应式。
- 运行：后端 `19040`、前端 `19041`、MySQL `19042`；Compose 自动 schema/seed/健康等待；Nginx 支持 SSE/WebSocket、缓存与安全响应头。
- 文档：README、架构、RBAC、使用、部署、安全、性能、CHANGELOG、验收证据和完成态审计已与代码同步。

### 测试与评估

| 验证 | 结果 |
| --- | --- |
| 后端 `mvn test` | 40 tests，0 failure/error/skipped，BUILD SUCCESS，1m31s |
| Agent 固定评估 | 38/38；准确率 100%；转人工召回 100%；失败分类为空 |
| 前端 `npm run check` | lint、typecheck、Vitest 8/8、production build 全通过 |
| 前端依赖审计 | TLS 校验开启；0 vulnerabilities |
| 并发 | 工单单创建、认领单赢家、SLA 单事件均通过 |
| 安全 | RBAC、ID 枚举、CSRF、Prompt 注入、敏感确认、日志脱敏通过 |
| 实时 | WebSocket 受众隔离/补放、SSE 取消和错误响应通过 |

### 运行与性能

- 全新 MySQL：15 表、4 用户、5 知识文档、3 seed 工单；全栈重启后 seed/业务计数不重复。
- 当前无缓存镜像：后端 `840424a19b33`、前端 `eb3a5df143f0`；Compose 自动验收 PASS（会话 #13、工单 `TK20260720-B7B6EDFC`）。
- 原生 H2 `19043` + Vite `19044` smoke 与完整自动验收 PASS（会话 #3、工单 `TK20260720-8FA47FB4`），临时服务已停止。
- 当前 MySQL：15 表、4 用户、5 知识文档、8 工单、13 会话、5 评价、6 工具执行单；flush level 1。
- 普通读：10 VU/20s，p95 250.57 ms，1,005/1,005 checks，错误率 0。
- 核心写：3 VU/20s，p95 554.01 ms，279/279 checks，错误率 0。
- SSE：3 VU/15s，p95 2.08 s，97/97 checks，错误率 0。
- Lighthouse：桌面 97/100/100；移动模拟 73/100/100；375/768/1440 真实浏览器无全局横向溢出。

### 最终浏览器 E2E

- 两个独立有界面 Chromium 会话，真实 Compose/MySQL 数据。
- 会话 #7、工单 `TK20260720-33A16047`。
- 同一会话完成：订单工具 → 负面情绪/明确人工 → 单工单 → 实时队列 → 原子认领 → 坐席/客户双向消息 → 解决 → 5 星评价 → 会话和工单关闭。
- 客户和坐席浏览器控制台均为 0 errors / 0 warnings。
- 最终截图：`docs/screenshots/e2e-live-customer-closed-final.png`、`docs/screenshots/e2e-live-agent-closed-final.png`。
- 封账前再次用两个隔离的有界面 Chromium 会话登录客户和坐席：会话 #7 的完整时间线仍可恢复，5 个快捷话术、输入框和发送按钮在关闭态均为 disabled；两端控制台再次为 0/0。

### 当前状态

- 阶段 1–5 全部完成；统一 7 项、专项 10 项和专项验收门槛均有直接证据，零已知 P0/P1 或核心可复现 P2 缺陷。
- 最终门禁：219 个文本候选质量检查、标准 `git diff --check`、Compose config/三容器健康、日志、`19040–19042` 端口、临时产物、残留项、13 个 Markdown 文件链接全部 PASS。
- Git 工作树共 203 个可解释条目：102 个修改、4 个删除、97 个新文件，全部位于目标仓库并属于本次完成态改造。
- 仅目标仓库有改动；未 commit、push 或发布。

### 本轮工具错误

| 错误 | 次数 | 处理 |
| --- | ---: | --- |
| `find` 行数统计参数被 Windows shell 错误解析 | 1 | 放弃该统计方式，直接读取文件与使用 `rg` 定位 |
| `docker inspect` / MySQL 查询在 PowerShell 与 cmd 嵌套时引号被提前解释 | 2 | 改用 PowerShell 原生单引号和参数变量；镜像、计数均成功读取 |
| `rg` 正则中的管道符被 cmd 当作命令管道 | 3 | 改用 PowerShell 单引号保护正则，测试、状态和残留项扫描成功 |
| Node 单行 Markdown 链接检查被 Windows 引号剥离 | 1 | 改用 PowerShell `[regex]` 与路径 API；13 个 Markdown 文件全部通过 |
| 最终残留扫描将错误记录中的检查名识别为任务标记 | 1 | 改写证据用语并重新执行完整门禁 |
| 临时关闭 `autocrlf` 后差异检查把 CRLF 误判为逐行空白 | 1 | 恢复仓库默认换行解释；标准 `git diff --check` 返回 0，不批量改写用户文件 |
