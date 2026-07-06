# 智答 AI Service Agent 部署指南

本文档面向**本地演示**与**生产化评估**。Mock LLM 模式可在零密钥下完整体验对话、工具调用与工单闭环。

## 1. 部署形态

```text
Internet
  ↓ HTTPS
Nginx / API Gateway（SSE 关闭缓冲，WebSocket 升级）
  ├─ /          → frontend nginx（Vue 静态）
  └─ /api / /ws → Spring Boot backend
                    ↓
                  MySQL 8.0
```

| 形态 | 用途 | 说明 |
| --- | --- | --- |
| **Docker Compose（推荐）** | 演示 / 集成测试 | MySQL + 后端 + 前端，默认 `LLM_PROVIDER=mock` |
| **本地 Maven + Vite** | 开发调试 | H2 内存库 + Mock，无需 MySQL |
| **生产** | 对外服务 | 需 HTTPS、托管 MySQL、真实 LLM 密钥与网关限流 |

## 2. Docker Compose 快速部署

```bash
cd ai-service-agent
cp .env.example .env   # 默认 Mock 即可；接真实模型时再填 LLM_API_KEY
docker compose up -d --build
docker compose ps
curl -f http://127.0.0.1:${BACKEND_HOST_PORT:-8081}/api/health
```

访问：

- 前端：http://localhost:8080（或 `.env` 中 `FRONTEND_HOST_PORT`）
- 后端健康：http://localhost:8081/api/health

## 3. 生产环境变量

必须覆盖：

```env
SPRING_PROFILES_ACTIVE=mysql
DATABASE_URL=jdbc:mysql://<host>:3306/ai_service_agent?...
DATABASE_USERNAME=<user>
DATABASE_PASSWORD=<strong-password>
LLM_PROVIDER=openai
LLM_API_KEY=<secret>
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
```

不要提交 `.env` 或真实密钥。Compose 默认 MySQL 密码仅用于本地演示。

## 4. 反向代理（HTTPS）

Nginx 需为 SSE 关闭缓冲，WebSocket 升级头正确转发。示例：

```nginx
location /api/ {
  proxy_pass http://127.0.0.1:8081/api/;
  proxy_http_version 1.1;
  proxy_set_header Host $host;
  proxy_set_header X-Forwarded-Proto https;
  proxy_buffering off;          # SSE 必须关闭缓冲
  proxy_read_timeout 300s;
}

location /ws/ {
  proxy_pass http://127.0.0.1:8081/ws/;
  proxy_http_version 1.1;
  proxy_set_header Upgrade $http_upgrade;
  proxy_set_header Connection "upgrade";
}
```

前端镜像内 `nginx.conf` 已代理 `/api` 与 `/ws`；公网入口建议在网关层终止 TLS 并限制来源 IP / 速率。

## 5. 健康检查与 smoke

```bash
curl -f http://127.0.0.1:8081/api/health
curl -f http://127.0.0.1:8081/api/dashboard/overview
```

## 6. 压测（k6 smoke）

后端启动后（Docker 默认端口 **8081**）：

```bash
# Linux / macOS
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8081 \
  -e VUS=20 \
  -e DURATION=1m \
  -v "$(pwd)/performance:/scripts" \
  grafana/k6:latest run /scripts/k6-smoke.js

# Windows PowerShell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8081 `
  -e VUS=20 `
  -e DURATION=1m `
  -v D:/project-hub/ai-service-agent/performance:/scripts `
  grafana/k6:latest run /scripts/k6-smoke.js
```

通过标准：`http_req_failed < 1%`，只读 API `p(95) < 800ms`。详见 [PERFORMANCE_REPORT.md](PERFORMANCE_REPORT.md)。

## 7. 回滚

1. 镜像使用 Git SHA 标签。
2. 发布前备份 MySQL 卷或逻辑备份。
3. `docker compose down` 后回退到上一版本镜像并 `up -d`。
