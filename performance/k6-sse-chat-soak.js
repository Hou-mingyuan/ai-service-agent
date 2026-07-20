import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    sse_soak: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || "1m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    "http_req_duration{type:sse}": ["p(95)<10000"],
    checks: ["rate>0.98"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:19040";
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 2);

const PROMPTS = [
  "帮我查订单123",
  "订单123的物流到哪了",
  "查询保单PAI2024001",
  "退货需要满足什么条件",
];

export function setup() {
  const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    username: __ENV.CUSTOMER_USERNAME || "customer",
    password: __ENV.CUSTOMER_PASSWORD || "customer123",
  }), { headers: { "Content-Type": "application/json" } });
  const ok = check(login, { "customer login ok": (r) => r.status === 200 });
  if (!ok) throw new Error(`customer login failed: ${login.status}`);
  return { token: login.json("data.accessToken") };
}

export default function (session) {
  const message = PROMPTS[__ITER % PROMPTS.length];
  const payload = JSON.stringify({
    clientMessageId: `k6:${__VU}:${__ITER}:${Date.now()}`,
    message,
  });

  const res = http.post(`${BASE_URL}/api/chat`, payload, {
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      Authorization: `Bearer ${session.token}`,
    },
    tags: { type: "sse" },
    timeout: "120s",
  });

  const ok = check(res, {
    "sse status 200": (r) => r.status === 200,
    "sse content-type": (r) =>
      (r.headers["Content-Type"] || "").includes("text/event-stream"),
    "sse has start": (r) => r.body && r.body.includes("event:start"),
    "sse has done": (r) => r.body && r.body.includes("event:done"),
  });

  if (ok) {
    // custom metric via check name mapping — k6 exposes checks as pass rate
  }

  sleep(THINK_TIME_SECONDS);
}
