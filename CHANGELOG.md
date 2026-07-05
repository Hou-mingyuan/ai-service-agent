# Changelog

本项目的所有重要变更均记录在此文件。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-07-04

### Added

- **垂直客服 Agent**：围绕电商 + 保险售后场景，6 个 Function Calling 工具（查订单/物流/保单、建单/查单、预约改期）
- **意图识别 + 情绪识别**：规则引擎实时判定意图与情绪；负面情绪自动升级转人工并建高优先级工单
- **工单全生命周期**：内置状态机 `OPEN → IN_PROGRESS → PENDING → RESOLVED → CLOSED`，流转留痕与时间线
- **SSE 流式对话**：逐 token 输出，实时展示工具调用参数与返回结果
- **坐席实时后台**：WebSocket 推送工单创建/更新事件
- **运营数据看板**：会话量、解决率、工单分布等统计
- **可插拔 LLM**：OpenAI 兼容接口 + 内置 Mock 模型；未配置密钥时自动回退 Mock，零密钥可完整体验
- **零依赖本地开发**：默认内存 H2 + Mock 模型，`mvn spring-boot:run` 即起
- **Docker 一键部署**：`docker-compose.yml` 启动 MySQL + 后端 + 前端，默认 Mock 无需 API Key
- **工程化**：JUnit 覆盖意图识别、工具路由、工单状态机与全链路集成测试
- **文档**：中文 README、架构说明（`docs/architecture.md`）、Mock 模式使用指南（`docs/USAGE.md`）

[1.0.0]: https://github.com/Hou-mingyuan/ai-service-agent/releases/tag/v1.0.0
