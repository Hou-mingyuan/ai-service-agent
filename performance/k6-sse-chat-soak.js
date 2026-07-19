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

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8081";
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 2);

const PROMPTS = [
  "帮我查订单123",
  "物流到哪了",
  "我想改配送时间",
  "查询保单P20240001",
];

export default function () {
  const message = PROMPTS[__ITER % PROMPTS.length];
  const payload = JSON.stringify({ message, userName: `k6-vu-${__VU}` });

  const res = http.post(`${BASE_URL}/api/chat`, payload, {
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
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
