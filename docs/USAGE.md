# 使用指南 · Mock 模式零密钥体验

本文档说明如何**不配置任何大模型 API Key**，通过内置 Mock 模型完整体验智答 AI 智能客服系统的对话、工具调用、转人工与工单闭环。

## 为什么可以零密钥运行？

项目默认 `LLM_PROVIDER=mock`。Mock 模型为离线内置、规则驱动，无需联网调用外部大模型，即可跑通「意图识别 → 工具路由 → 流式答复 → 转人工建单」全链路。适合本地体验、演示、CI 与单测。

> 若要接入 OpenAI / DeepSeek / 通义 / Ollama 等真实模型，见根目录 [README.md](../README.md)「配置说明」；本指南专注零密钥 Mock 路径。

## 前置条件

- **Docker 方式**：已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（含 Docker Compose），本机端口 **8080**、**8081** 未被占用
- **本地开发方式**：JDK 17+、Maven 3.8+；前端需 Node.js 20.19+ 或 22.12+

## 方式一：Docker 一键体验（推荐）

无需复制或编辑 `.env`，直接启动：

```bash
cd ai-service-agent
docker compose up -d --build
```

Compose 默认注入 `LLM_PROVIDER=mock`，**不需要** `LLM_API_KEY`。

查看启动状态：

```bash
docker compose ps
docker compose logs -f backend
```

| 入口 | 地址 |
| --- | --- |
| **前端（对话 / 工单 / 看板）** | http://localhost:8080 |
| **后端健康检查** | http://localhost:8081/api/health |

若 8080 / 8081 已被占用，可在 `.env` 中改端口后启动：

```bash
FRONTEND_HOST_PORT=18082
BACKEND_HOST_PORT=18083
docker compose up -d --build
```

## 方式二：本地开发（零外部依赖）

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认使用内存 H2 数据库 + Mock 模型，接口：http://localhost:8080/api/health

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端：http://localhost:5173（已代理 `/api`、`/ws` 到后端 8080）

## Mock 模式体验路径

打开前端「智能对话」，依次尝试以下话术，观察 Agent 工具调用与流式回答：

| 步骤 | 输入示例 | 预期行为 |
| --- | --- | --- |
| 1. 查订单 | `帮我查订单123` | 调用 `query_order`，返回订单详情 |
| 2. 查物流 | `订单123的快递到哪了` | 调用 `query_logistics`，展示物流轨迹 |
| 3. 查保单 | `查一下保单 P20240001` | 调用 `query_policy` |
| 4. 建工单 | `我要申请退款，订单123` | 调用 `create_ticket` 创建售后工单 |
| 5. 查工单 | `我的工单进度怎么样了` | 调用 `query_ticket` |
| 6. 改期 | `把订单123的配送改到明天` | 调用 `reschedule_appointment` |
| 7. 转人工 | `你们太差了，我要投诉！` | 情绪识别为负面，**自动建高优先级工单并转人工** |

### 坐席与看板

1. 切换到 **「坐席工单」**：步骤 7 后应实时收到新工单（WebSocket 推送），可指派、流转状态
2. 切换到 **「数据看板」**：查看会话量、解决率、工单分布

### 示例业务数据

内置 Mock 业务库含虚构订单、保单、物流与工单数据，可直接用 README 中的订单号/保单号提问。也可通过 API 浏览：

```bash
curl http://localhost:8081/api/catalog/orders
curl http://localhost:8081/api/catalog/policies
```

（本地 `mvn` 模式将端口改为 `8080`。）

## 验证 Mock 已生效

```bash
# Docker
curl http://localhost:8081/api/health

# 查看后端日志，应出现类似：
# LLM 供应方：mock（离线内置，无需密钥）
```

Mock 模式下工具由规则路由选定（非大模型自主决策），但对前端与编排层完全透明，界面表现与真实模型一致。

## 运行测试（Mock 全链路）

```bash
cd backend
mvn test
```

覆盖意图识别、情绪识别、工单状态机，以及 Mock 模式下的工具路由与转人工集成测试（`ToolRoutingTest`）。

## 停止与清理

```bash
# Docker：停止服务（保留 MySQL 数据卷）
docker compose down

# 彻底重置（删除数据卷）
docker compose down -v
```

## 常见问题

### 启动后对话无响应

确认后端健康检查通过后再访问前端；Docker 需等待 MySQL 健康检查完成。

### 想切换到真实大模型

复制环境变量模板并按需填写 Key：

```bash
cp .env.example .env
# 编辑 LLM_PROVIDER=openai 与 LLM_API_KEY=sk-...
docker compose up -d --force-recreate backend
```

未配置 `LLM_API_KEY` 时，即使设为 `openai` 也会安全回退到 Mock。

### 端口冲突

在 `.env` 中设置 `FRONTEND_HOST_PORT` / `BACKEND_HOST_PORT`，或关闭占用 8080/8081 的进程。

## 下一步

- 架构与时序图见 [architecture.md](architecture.md)
- API 完整列表见根目录 [README.md](../README.md)
- 版本历史见 [CHANGELOG.md](../CHANGELOG.md)
