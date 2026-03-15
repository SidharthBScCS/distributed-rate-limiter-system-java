import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const USERNAME = __ENV.AUTH_USER || "admin";
const PASSWORD = __ENV.AUTH_PASS || "admin";
const ROUTE = __ENV.TEST_ROUTE || "/api/test";
const TOKENS = Number(__ENV.TOKENS || "1");
const SLEEP_MS = Number(__ENV.SLEEP_MS || "100");

const apiKeysEnv = __ENV.API_KEYS || "";
const API_KEYS = apiKeysEnv
  .split(",")
  .map((v) => v.trim())
  .filter((v) => v.length > 0);

if (API_KEYS.length === 0) {
  throw new Error("Set API_KEYS env var with real keys, e.g. API_KEYS=key1,key2,key3");
}

export const allowedRequests = new Counter("allowed_requests");
export const blockedRequests = new Counter("blocked_requests");
export const otherResponses = new Counter("other_responses");
export const loginFailures = new Counter("login_failures");
export const limiterSuccessRate = new Rate("limiter_success_rate");

export const options = {
  scenarios: {
    sliding_window_load: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "20s", target: 10 },
        { duration: "40s", target: 50 },
        { duration: "40s", target: 100 },
        { duration: "20s", target: 0 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.10"],
    limiter_success_rate: ["rate>0.90"],
  },
};

function login() {
  const payload = JSON.stringify({ username: USERNAME, password: PASSWORD });
  const response = http.post(`${BASE}/api/auth/login`, payload, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "auth_login" },
  });

  const ok = check(response, {
    "login status is 200": (r) => r.status === 200,
  });

  if (!ok) {
    loginFailures.add(1);
    return null;
  }

  return response.cookies;
}

export function setup() {
  return {
    keyCount: API_KEYS.length,
    route: ROUTE,
    tokens: TOKENS,
  };
}

export default function (data) {
  const apiKey = API_KEYS[(__VU - 1) % API_KEYS.length];
  const cookies = login();

  if (!cookies) {
    limiterSuccessRate.add(false);
    return;
  }

  const payload = JSON.stringify({
    apiKey,
    route: data.route,
    tokens: data.tokens,
  });

  const response = http.post(`${BASE}/api/limit/check`, payload, {
    headers: { "Content-Type": "application/json" },
    cookies,
    tags: {
      name: "limit_check",
      api_key_slot: String((__VU - 1) % API_KEYS.length),
      route: data.route,
    },
  });

  const ok = check(response, {
    "limit check returns 200 or 429": (r) => r.status === 200 || r.status === 429,
  });
  limiterSuccessRate.add(ok);

  if (response.status === 200) {
    allowedRequests.add(1, { api_key: apiKey });
  } else if (response.status === 429) {
    blockedRequests.add(1, { api_key: apiKey });
  } else {
    otherResponses.add(1, { api_key: apiKey, status: String(response.status) });
  }

  if (SLEEP_MS > 0) {
    sleep(SLEEP_MS / 1000);
  }
}
