# Security Policy

## Supported Versions

**智答 AI Service Agent** is actively maintained on the `main` branch. Security fixes target `main` first.

| Version | Supported |
| --- | --- |
| Latest on `main` | Yes |
| Older tags | Best effort |

## Reporting a Vulnerability

Do **not** open a public issue with exploit details, credentials, API keys, or proof-of-concept code.

Preferred channels:

1. GitHub **Private vulnerability reporting** or a **Security Advisory** for this repository, if enabled.
2. If private reporting is unavailable, open a public issue asking for a private contact channel **without** technical exploit details.

Include affected version/commit, reproduction steps, impact, and relevant logs with secrets removed.

## Scope

### In scope

- Spring Boot REST / SSE / WebSocket APIs
- Docker Compose deployment and default credentials
- LLM gateway configuration and secret handling
- Mock demo data leakage or misconfiguration in production

### Out of scope (current release)

- End-user / agent RBAC and audit logging (see README Roadmap)
- Third-party LLM provider security posture

## Production Baseline

- Override compose default MySQL passwords (`csagentpwd` / `rootpwd`) and never expose MySQL port publicly.
- Set `LLM_API_KEY` via secrets manager; do not commit `.env`.
- Place HTTPS termination and rate limiting at a reverse proxy or API gateway.
- Mock LLM mode is for **local demo / CI only** — disable in production unless intentionally isolated.

### Secret Handling

| Variable | Purpose |
| --- | --- |
| `LLM_API_KEY` | Real LLM gateway key (BYOK) |
| `DATABASE_PASSWORD` | MySQL credential in production |
| `LLM_BASE_URL` | May embed tenant routing; treat as sensitive in multi-tenant setups |

Never commit `.env` with real keys. Use `.env.example` as the template.

### Dependency Audit

Local checks:

```bash
cd backend && mvn -q dependency-check:check
cd ../frontend && npm audit --audit-level=high
```

### Demo Data

Built-in orders, policies, and tickets are **fictional**. Do not deploy Mock catalog data to production without replacing with real integrations.

See [DEPLOYMENT.md](DEPLOYMENT.md) for deployment hardening.
