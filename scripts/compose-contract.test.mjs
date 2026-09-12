import assert from 'node:assert/strict';
import { test } from 'node:test';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const production = {
  DATABASE_PASSWORD: 'contract-db-password', MYSQL_ROOT_PASSWORD: 'contract-root-password',
  APP_SECURITY_JWT_SECRET: 'contract-jwt-secret-with-at-least-32-characters',
  APP_CORS_ALLOWED_ORIGINS: 'https://support.example.test',
  LLM_API_KEY: 'contract-llm-key', BUSINESS_BASE_URL: 'https://business.example.test',
  BUSINESS_API_TOKEN: 'contract-business-token',
};

function render(values, prod = false) {
  const directory = mkdtempSync(join(tmpdir(), 'csagent-compose-'));
  try {
    const envFile = join(directory, '.env');
    writeFileSync(envFile, Object.entries(values).map(([k, v]) => `${k}=${v}`).join('\n'));
    const args = process.env.COMPOSE_BIN ? [] : ['compose'];
    args.push('--env-file', envFile, '-f', 'docker-compose.yml');
    if (prod) args.push('-f', 'docker-compose.production.yml');
    args.push('config', '--format', 'json');
    const env = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
      !/^(APP_|BUSINESS_|LLM_|SLA_|DATABASE_|MYSQL_|COMPOSE_)/.test(key)));
    return spawnSync(process.env.COMPOSE_BIN || 'docker', args, { cwd: root, env, encoding: 'utf8' });
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test('demo defaults remain runnable and bind database to loopback', () => {
  const result = render({});
  assert.equal(result.status, 0, result.stderr || result.error?.message);
  const config = JSON.parse(result.stdout);
  assert.equal(config.services.backend.environment.APP_DEMO_ENABLED, 'true');
  assert.equal(config.services.backend.environment.BUSINESS_ADAPTER, 'mock');
  assert.equal(config.services.mysql.ports[0].host_ip, '127.0.0.1');
});

test('env-file security and business values reach the backend container', () => {
  const values = { ...production, APP_DEMO_ENABLED: 'false', APP_SECURITY_RBAC_ENABLED: 'true',
    APP_SECURITY_ANONYMOUS_CHAT: 'false', APP_SECURITY_COOKIE_SECURE: 'true',
    APP_SECURITY_JWT_EXPIRATION_MINUTES: '20', APP_SECURITY_COOKIE_NAME: 'CUSTOM_AUTH',
    APP_SECURITY_COOKIE_SAME_SITE: 'Lax', LLM_ALLOW_PRIVATE_BASE_URL: 'true',
    BUSINESS_ADAPTER: 'http', BUSINESS_TIMEOUT_SECONDS: '11', BUSINESS_MAX_RETRIES: '2' };
  const result = render(values);
  assert.equal(result.status, 0, result.stderr);
  const actual = JSON.parse(result.stdout).services.backend.environment;
  for (const [key, value] of Object.entries(values)) {
    if (key !== 'MYSQL_ROOT_PASSWORD') assert.equal(actual[key], value, key);
  }
});

test('production override enforces mode even with demo settings in env-file', () => {
  const result = render({ ...production, APP_DEMO_ENABLED: 'true', LLM_PROVIDER: 'mock',
    BUSINESS_ADAPTER: 'mock', APP_SECURITY_RBAC_ENABLED: 'false', APP_SECURITY_COOKIE_SECURE: 'false' }, true);
  assert.equal(result.status, 0, result.stderr);
  const actual = JSON.parse(result.stdout).services.backend.environment;
  assert.equal(actual.APP_DEMO_ENABLED, 'false');
  assert.equal(actual.APP_SECURITY_RBAC_ENABLED, 'true');
  assert.equal(actual.APP_SECURITY_COOKIE_SECURE, 'true');
  assert.equal(actual.LLM_PROVIDER, 'openai');
  assert.equal(actual.BUSINESS_ADAPTER, 'http');
});

test('production rejects each missing required input before starting containers', () => {
  for (const key of Object.keys(production)) {
    const values = { ...production, [key]: '' };
    const result = render(values, true);
    assert.notEqual(result.status, 0, key);
    assert.ok(result.stderr.includes(key), `missing variable was not identified: ${key}`);
  }
});
