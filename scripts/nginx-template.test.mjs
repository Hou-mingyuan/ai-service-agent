import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

// nginx 镜像的 envsubst 只替换"运行时已定义"的环境变量：模板里出现 Dockerfile
// 未声明的 ${VAR} 会在容器启动时原样残留在配置里，nginx 直接解析失败。
// 本测试保证模板占位符集合与 Dockerfile ENV 声明保持一致，渲染结果可用。
const root = fileURLToPath(new URL('..', import.meta.url));
const template = readFileSync(join(root, 'frontend', 'nginx.conf.template'), 'utf8');
const dockerfile = readFileSync(join(root, 'frontend', 'Dockerfile'), 'utf8');

const declaredEnv = new Map(
  [...dockerfile.matchAll(/^ENV\s+([A-Za-z_][A-Za-z0-9_]*)=(.*)$/gm)].map(([, key, value]) => [key, value]),
);
const placeholders = [...new Set([...template.matchAll(/\$\{([A-Za-z_][A-Za-z0-9_]*)\}/g)].map((m) => m[1]))];

test('every template placeholder is declared in the frontend Dockerfile ENV', () => {
  assert.ok(placeholders.length > 0, 'template has no ${VAR} placeholders to verify');
  const undeclared = placeholders.filter((key) => !declaredEnv.has(key));
  assert.deepEqual(undeclared, [], `placeholders missing from Dockerfile ENV: ${undeclared.join(', ')}`);
});

test('rendered config is fully substituted and keeps the proxy contract', () => {
  const rendered = template.replace(/\$\{([A-Za-z_][A-Za-z0-9_]*)\}/g, (whole, key) => {
    assert.ok(declaredEnv.has(key), `unexpected placeholder: ${key}`);
    return declaredEnv.get(key);
  });
  assert.doesNotMatch(rendered, /\$\{[A-Za-z_][A-Za-z0-9_]*\}/, 'placeholder survived rendering');
  assert.match(rendered, /^    listen 19041;$/m);
  assert.match(rendered, /proxy_pass http:\/\/backend:19040;/);
  assert.match(rendered, /location \/api\//);
  assert.match(rendered, /location \/ws\//);
  assert.match(rendered, /try_files \$uri \$uri\/ \/index\.html;/);
});

test('Dockerfile still installs the template where the nginx entrypoint renders it', () => {
  assert.match(dockerfile, /COPY nginx\.conf\.template \/etc\/nginx\/templates\/default\.conf\.template/);
});
