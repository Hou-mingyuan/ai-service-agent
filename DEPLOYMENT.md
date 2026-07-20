# 部署指南

推荐路径是 Docker Compose：MySQL、Spring Boot 与前端 Nginx 一次启动，自动建表、写入虚构 Demo 数据并等待三服务健康。项目只使用 `19040–19049`，默认分别为后端 `19040`、前端 `19041`、MySQL `19042`。

## 环境要求

- Docker Engine 24+ 或 Docker Desktop，Compose v2
- 建议 4 核 CPU、6 GB 可用内存、4 GB 可用磁盘
- 首次构建需要访问 Docker Hub 和 Maven Central；网络受限时可设置 `MAVEN_MIRROR_URL`
- 自动验收另需 Node.js 22

## Demo 一键启动

```bash
cp .env.example .env
docker compose up -d --build --wait --wait-timeout 240
docker compose ps
node scripts/acceptance-smoke.mjs
```

Windows `cmd` 将第一行改为：

```bat
copy .env.example .env
```

健康检查：

```bash
curl -f http://127.0.0.1:19040/api/health
curl -f http://127.0.0.1:19040/api/ready
```

`/api/health` 会明确返回 `mode`、LLM provider、业务 adapter 的 `mock/source/status` 以及知识来源。Compose 健康检查不会仅验证端口打开。

## 数据初始化与重启

- `schema.sql` 使用 `IF NOT EXISTS` 初始化 15 张表；任何 DDL 错误都会阻止后端启动，不允许部分 schema 仍报告健康。
- Demo seed 按自然键检查，重启不会重复创建 4 个账号、5 个知识文档或基础业务数据。
- `docker compose restart` 后可执行 `docker compose up -d --wait --wait-timeout 240` 等待恢复。
- `docker compose down` 保留 MySQL 卷；`docker compose down -v` 会永久删除当前项目的演示数据。

本发布候选版验证的是全新库初始化。将已有旧版本数据库升级前，必须先备份并为实际旧 schema 准备受审查的迁移脚本，不能依赖 `IF NOT EXISTS` 修改既有列。

## 生产配置门禁

Demo Compose 账号和数据库密码不能用于生产。生产至少配置：

```env
APP_DEMO_ENABLED=false
APP_TIME_ZONE=Asia/Shanghai
APP_SECURITY_RBAC_ENABLED=true
APP_SECURITY_ANONYMOUS_CHAT=false
APP_SECURITY_JWT_SECRET=<至少32字符且不是仓库默认值>
APP_SECURITY_COOKIE_SECURE=true
APP_CORS_ALLOWED_ORIGINS=https://support.example.com

SPRING_PROFILES_ACTIVE=mysql
DATABASE_URL=jdbc:mysql://<private-host>:3306/ai_service_agent?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Shanghai
DATABASE_USERNAME=<managed-user>
DATABASE_PASSWORD=<secret-manager-reference>

LLM_PROVIDER=openai
LLM_BASE_URL=https://<approved-llm-gateway>/v1
LLM_MODEL=<approved-model>
LLM_API_KEY=<secret>

BUSINESS_ADAPTER=http
BUSINESS_BASE_URL=https://<business-api>
BUSINESS_API_TOKEN=<secret>
```

门禁会拒绝以下组合：关闭 RBAC、匿名对话、开发 JWT 密钥、通配 CORS、Mock LLM、Mock 业务 adapter 或非 MySQL profile。真实 LLM/业务 adapter 缺配置同样启动失败，不会自动回退 Mock。

真实 LLM base URL 默认禁止回环、链路本地和私网地址。确需访问受控内网网关时，审查网络边界后显式设置 `LLM_ALLOW_PRIVATE_BASE_URL=true`。

## 反向代理

公网入口应终止 TLS、限制请求大小和速率，并保持 SSE/WebSocket 语义：

```nginx
location /api/ {
  proxy_pass http://127.0.0.1:19040/api/;
  proxy_http_version 1.1;
  proxy_set_header Host $host;
  proxy_set_header X-Forwarded-Proto https;
  proxy_buffering off;
  proxy_read_timeout 300s;
}

location /ws/ {
  proxy_pass http://127.0.0.1:19040/ws/;
  proxy_http_version 1.1;
  proxy_set_header Upgrade $http_upgrade;
  proxy_set_header Connection "upgrade";
}
```

前端镜像内 Nginx 已正确代理 `/api` 和 `/ws`，并关闭 SSE 缓冲。生产不应暴露 MySQL `19042` 到公网。

## 验收与监控

```bash
node scripts/acceptance-smoke.mjs
docker compose logs --since 10m backend
docker compose ps
```

自动验收会创建一条虚构闭环数据，适合 Demo/预发布环境，不要对生产数据直接运行。

Actuator 仅暴露 `health/info/metrics`，健康详情不返回密钥。建议在外部系统监控：

- `/api/ready` 与容器重启次数
- SSE 业务错误率和端到端耗时
- WebSocket 断连、重放积压和受众拒绝
- SLA 预警/超时事件与定时扫描失败
- adapter 超时、5xx、重试和熔断趋势

## 回滚

1. 发布前记录镜像 digest 并备份 MySQL。
2. 停止写流量，确认没有正在执行的敏感执行单。
3. 回退到上一组镜像；若 schema 发生变化，执行对应的已审核回退迁移。
4. 等待健康检查并运行只读 smoke；确认后恢复流量。

## 常见故障

| 现象 | 判断与处理 |
| --- | --- |
| Maven 构建下载失败 | 检查网络；临时设置可信 `MAVEN_MIRROR_URL`，不要把镜像写死进源码 |
| 后端 unhealthy | 查看第一条启动异常；配置门禁、adapter 配置和 schema 错误都会故意阻止启动 |
| 对话无流式输出 | 检查代理是否关闭缓冲、`Accept: text/event-stream` 和 90 秒超时 |
| WebSocket 重连后缺事件 | 使用最后成功事件 id 作为 `/ws/events?afterId=`；也可先读 `/api/events` |
| Cookie 写操作 403 | 先读取 `/api/auth/csrf`，携带 CSRF Cookie 与请求头；Bearer 模式不需要 CSRF |
| 日期偏移 | 确认 `APP_TIME_ZONE=Asia/Shanghai` 和数据库连接 `serverTimezone=Asia/Shanghai` |
