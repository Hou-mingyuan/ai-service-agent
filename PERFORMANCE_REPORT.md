# 性能报告

## 结论

本次在固定零密钥 Demo fixture、Docker Compose、MySQL 8 和 `19040–19042` 端口下完成复测。最终默认配置保留 `innodb_flush_log_at_trx_commit=1`，没有用降低持久性换取验收数字。

| 场景 | 负载 | 最终 p95 | 预算 | 业务错误率 | 结论 |
| --- | --- | ---: | ---: | ---: | --- |
| 普通读：健康 + 运营看板 | 10 VU，20s，think 0.2s | **250.57 ms** | `< 300 ms` | **0%** | PASS |
| 核心写：创建工单 | 3 VU，20s，think 0.2s | **554.01 ms** | `< 800 ms` | **0%** | PASS |
| Mock SSE 对话 | 3 VU，15s，think 1s | **2.08 s** | `< 10 s`，与本地 CRUD 分开 | **0%** | PASS |
| 登录页 Lighthouse（桌面工具场景） | Lighthouse 13.4.0 desktop | **97 / 100 / 100** | Performance ≥ 90，A11y/BP ≥ 90 | 控制台 0 错误 | PASS |

Lighthouse 三项依次为 Performance、Accessibility、Best Practices。本项目是登录后的客服工作台，不考核 SEO。

## 环境

- Windows 11、Docker Desktop / Engine 29.6.1
- Compose：MySQL 8、Java 17 后端、Nginx 1.27 前端
- 主机同时运行其他仓库容器，因此保留了冷态抖动数据，不把最好的一次结果冒充唯一结果
- Demo 数据：Mock LLM、Mock 业务 adapter、本地真实词法知识检索
- 后端 `19040`、前端 `19041`、MySQL `19042`

## 可复现命令

```bash
docker compose up -d --build --wait --wait-timeout 240

docker run --rm \
  -e BASE_URL=http://host.docker.internal:19040 \
  -e VUS=10 -e DURATION=20s -e THINK_TIME_SECONDS=0.2 \
  -v "$PWD/performance:/scripts" \
  --add-host=host.docker.internal:host-gateway \
  grafana/k6:latest run /scripts/k6-smoke.js

docker run --rm \
  -e BASE_URL=http://host.docker.internal:19040 \
  -e VUS=3 -e DURATION=20s -e THINK_TIME_SECONDS=0.2 \
  -v "$PWD/performance:/scripts" \
  --add-host=host.docker.internal:host-gateway \
  grafana/k6:latest run /scripts/k6-write-smoke.js

docker run --rm \
  -e BASE_URL=http://host.docker.internal:19040 \
  -e VUS=3 -e DURATION=15s -e THINK_TIME_SECONDS=1 \
  -v "$PWD/performance:/scripts" \
  --add-host=host.docker.internal:host-gateway \
  grafana/k6:latest run /scripts/k6-sse-chat-soak.js
```

Windows PowerShell 将挂载参数改为 `-v "${PWD}\performance:/scripts"`。

## 结果明细

### 普通读接口

- 1,005 请求，502 次业务迭代
- 1,005 / 1,005 checks 通过
- tagged fast：平均 106.39 ms，p90 198.56 ms，p95 **250.57 ms**
- HTTP failed：**0 / 1,005**

压测脚本将阈值从旧版 800 ms 校准为统一标准要求的 300 ms，并在 CI 中使用相同 10 VU / 20s / 0.2s 配置。

### 核心本地写

- 140 请求，139 次工单创建迭代
- 279 / 279 checks 通过
- tagged write：平均 233.39 ms，p90 433.35 ms，p95 **554.01 ms**
- HTTP failed：**0 / 140**
- 每个请求使用独立幂等键，并校验 `replayed=false` 与单一工单结果

写路径优化前，3 VU / 10s 的 p95 为 **1.97 s**。优化内容：删除成功路径预查询，将工单、时间线、单一 CASE 实时事件和成功审计合并到一个事务，Socket 仅在提交后广播。默认持久化配置下曾有一次 3 VU / 15s 冷态运行 p95 **849.77 ms**；延长为固定 20s 后 p95 为 554.01 ms。所有运行错误率均为 0。

诊断期间测试过 `innodb_flush_log_at_trx_commit=2`，p95 可降至 323.78 ms；因该设置允许操作系统崩溃时丢失约一秒事务日志，最终配置已恢复并验证为 **1**，该数字不作为完成依据。

### SSE 对话

- 25 请求，24 次完整对话迭代
- 97 / 97 checks 通过
- 校验 HTTP 200、`text/event-stream`、`event:start` 和 `event:done`
- tagged SSE：平均 1.02 s，p95 **2.08 s**
- HTTP failed：**0 / 25**

SSE 包含确定性 Mock LLM 与工具过程，按统一标准与普通本地 CRUD 分开统计。

## 前端优化与 Lighthouse

基线为所有页面一次性打入单包：JS 184.24 KiB，gzip **65.29 KiB**。改为路由按页懒加载后：

- 主包 110.70 KiB，gzip **43.51 KiB**
- 登录页 chunk 4.23 KiB，gzip **2.19 KiB**
- 客户对话页仅在进入时加载，gzip **8.33 KiB**
- 未使用 JS 估算从 88 KiB 降到 36 KiB
- 哈希资源增加一年 immutable 缓存，`index.html` 保持 `no-cache`

| 审计 | Performance | Accessibility | Best Practices | FCP | LCP | TBT | CLS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 移动模拟，优化前 | 73 | 100 | 100 | 2.1 s | 3.0 s | 830 ms | 0 |
| 移动模拟，懒加载后 | 73 | 100 | 100 | **1.7 s** | 3.0 s | 880 ms | 0 |
| 桌面工具场景，懒加载后 | **97** | **100** | **100** | **0.5 s** | **1.0 s** | **110 ms** | **0** |

移动模拟 TBT 受共享主机 CPU 争用波动，不能据此声称移动性能达到公开内容站 85 分门槛。本项目采用桌面内部工具预算；移动端另外用真实浏览器在 `375×812`、`768×1024` 验证，无页面级横向溢出、遮挡或不可达核心动作。

原始报告：

- `docs/lighthouse-login.json`：优化前移动模拟
- `docs/lighthouse-login-after.json`：懒加载后移动模拟
- `docs/lighthouse-login-desktop.json`：最终桌面工具场景

## 性能边界

- 这里证明的是本地 Demo / fixture 性能，不包含真实 OpenAI-compatible provider 或真实业务系统网络延迟。
- 真实 adapter 有独立超时、有限重试和错误分类；上线前必须在目标网络与真实依赖下另建 SLO。
- 当前数据量为演示规模；长周期容量、跨地域和多副本吞吐不是本地报告的结论。
