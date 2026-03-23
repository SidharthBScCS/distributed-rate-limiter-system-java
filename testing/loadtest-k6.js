import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const USERNAME = __ENV.AUTH_USER || "admin";
const PASSWORD = __ENV.AUTH_PASS || "admin";
const ROUTE = __ENV.TEST_ROUTE || "/api/test";
const TOKENS = Number(__ENV.TOKENS || "1");
const SLEEP_MS = Number(__ENV.SLEEP_MS || "25");
const START_VUS = Number(__ENV.START_VUS || "0");
const STAGE_1_TARGET = Number(__ENV.STAGE_1_TARGET || "100");
const STAGE_2_TARGET = Number(__ENV.STAGE_2_TARGET || "300");
const STAGE_3_TARGET = Number(__ENV.STAGE_3_TARGET || "600");
const STAGE_4_TARGET = Number(__ENV.STAGE_4_TARGET || "1000");
const STAGE_1_DURATION = __ENV.STAGE_1_DURATION || "30s";
const STAGE_2_DURATION = __ENV.STAGE_2_DURATION || "1m";
const STAGE_3_DURATION = __ENV.STAGE_3_DURATION || "1m";
const STAGE_4_DURATION = __ENV.STAGE_4_DURATION || "1m";
const STAGE_5_DURATION = __ENV.STAGE_5_DURATION || "30s";
const API_KEY_STRATEGY = (__ENV.API_KEY_STRATEGY || "per-vu").toLowerCase();
const RANDOM_SEED = Number(__ENV.RANDOM_SEED || "17");

function parseApiKeys(raw) {
  return raw
    .split(/[\r\n,]+/)
    .map((v) => v.trim())
    .filter((v) => v.length > 0);
}

const apiKeysFile = __ENV.API_KEYS_FILE;
const apiKeysEnv = __ENV.API_KEYS || "";
const apiKeysRaw = apiKeysFile ? open(apiKeysFile) : apiKeysEnv;
const API_KEYS = parseApiKeys(apiKeysRaw);

if (API_KEYS.length === 0) {
  throw new Error(
    "Set API_KEYS or API_KEYS_FILE with real keys, e.g. API_KEYS=key1,key2,key3 or API_KEYS_FILE=./api-keys.txt"
  );
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
      startVUs: START_VUS,
      stages: [
        { duration: STAGE_1_DURATION, target: STAGE_1_TARGET },
        { duration: STAGE_2_DURATION, target: STAGE_2_TARGET },
        { duration: STAGE_3_DURATION, target: STAGE_3_TARGET },
        { duration: STAGE_4_DURATION, target: STAGE_4_TARGET },
        { duration: STAGE_5_DURATION, target: 0 },
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

  return Object.fromEntries(
    Object.entries(response.cookies).map(([name, values]) => [name, values?.[0]?.value ?? ""])
  );
}

function selectApiKey() {
  if (API_KEY_STRATEGY === "random") {
    const index = Math.floor((Math.random() + ((__VU + __ITER + RANDOM_SEED) % 13) / 13) * API_KEYS.length) % API_KEYS.length;
    return API_KEYS[index];
  }
  return API_KEYS[(__VU - 1) % API_KEYS.length];
}

export function setup() {
  const cookies = login();
  if (!cookies) {
    throw new Error("Setup login failed. Check backend status and admin credentials.");
  }

  return {
    keyCount: API_KEYS.length,
    route: ROUTE,
    tokens: TOKENS,
    cookies,
  };
}

export default function (data) {
  const apiKey = selectApiKey();

  const payload = JSON.stringify({
    apiKey,
    route: data.route,
    tokens: data.tokens,
  });

  const response = http.post(`${BASE}/api/limit/check`, payload, {
    headers: { "Content-Type": "application/json" },
    cookies: data.cookies,
    tags: {
      name: "limit_check",
      api_key_slot: String(API_KEYS.indexOf(apiKey)),
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
