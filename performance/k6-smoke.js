import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "1m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{type:fast}": ["p(95)<300"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:19040";
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 1);

export function setup() {
  const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    username: __ENV.SUPERVISOR_USERNAME || "supervisor",
    password: __ENV.SUPERVISOR_PASSWORD || "super123",
  }), { headers: { "Content-Type": "application/json" } });
  const ok = check(login, { "supervisor login ok": (r) => r.status === 200 });
  if (!ok) throw new Error(`supervisor login failed: ${login.status}`);
  return { token: login.json("data.accessToken") };
}

export default function (session) {
  const health = http.get(`${BASE_URL}/api/health`, { tags: { type: "fast" } });
  check(health, { "health ok": (r) => r.status === 200 });

  const overview = http.get(`${BASE_URL}/api/dashboard/overview`, {
    headers: { Authorization: `Bearer ${session.token}` },
    tags: { type: "fast" },
  });
  check(overview, { "dashboard ok": (r) => r.status === 200 });

  sleep(THINK_TIME_SECONDS);
}
