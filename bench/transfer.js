import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'uniform';
const USERS = parseInt(__ENV.USERS || '200', 10);
const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '30s';
const WARMUP = __ENV.WARMUP || '10s';

export const transfers_201 = new Counter('transfers_201');
export const transfers_422 = new Counter('transfers_422');
export const transfers_other = new Counter('transfers_other');

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: VUS,
      duration: WARMUP,
      tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-vus',
      vus: VUS,
      startTime: WARMUP,
      duration: DURATION,
      tags: { phase: 'measure' },
    },
  },
  thresholds: {
    'http_req_failed{phase:measure}': ['rate<0.001'],
    'http_req_duration{phase:measure}': ['p(95)<2000'],
    'http_reqs{phase:measure}': ['count>0'],
    'transfers_201{phase:measure}': ['count>0'],
    'transfers_422{phase:measure}': ['count>=0'],
    'transfers_other{phase:measure}': ['count>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Retry a request up to maxRetries times with delayMs between attempts.
// Returns the last response (or throws if all retries fail with a non-HTTP error).
function postWithRetry(url, body, params, maxRetries, delayMs) {
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const res = http.post(url, body, params);
    // A real HTTP response (even 4xx/5xx) is a success at the transport layer
    if (res.status > 0) {
      return res;
    }
    // status === 0 means a network error (connection refused, reset, etc.)
    if (attempt < maxRetries) {
      sleep(delayMs / 1000);
    }
  }
  // Return the last failed response so the caller can inspect it
  return http.post(url, body, params);
}

export function setup() {
  const runId = Math.random().toString(36).substring(2, 10);
  const userIds = [];

  for (let i = 0; i < USERS; i++) {
    // Retry up to 5 times with 2s delay to tolerate transient startup
    // connection errors when the app container has just restarted.
    const userRes = postWithRetry(
      `${BASE_URL}/api/v1/users`,
      JSON.stringify({
        name: `bench-${runId}-${i}`,
        email: `bench-${runId}-${i}@example.com`,
      }),
      { headers: { 'Content-Type': 'application/json' } },
      5,
      2000
    );
    check(userRes, { 'user created': (r) => r.status === 201 });
    if (userRes.status !== 201) {
      throw new Error(`Failed to create user ${i}: HTTP ${userRes.status} body=${userRes.body}`);
    }
    const user = JSON.parse(userRes.body);
    userIds.push(user.userId);

    const topupKey = `bench-topup-${runId}-${i}`;
    const topupRes = postWithRetry(
      `${BASE_URL}/api/v1/wallets/${user.userId}/topup`,
      JSON.stringify({ amountPaise: 10000000 }),
      {
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': topupKey,
        },
      },
      5,
      2000
    );
    check(topupRes, { 'topup succeeded': (r) => r.status === 201 });
    if (topupRes.status !== 201) {
      throw new Error(`Failed to topup wallet for user ${user.userId}: HTTP ${topupRes.status} body=${topupRes.body}`);
    }
  }

  return { runId, userIds, hotUser: userIds[0] };
}

export default function (data) {
  const { runId, userIds, hotUser } = data;
  let fromUserId, toUserId, amountPaise;

  if (SCENARIO === 'uniform') {
    const senderIdx = Math.floor(Math.random() * userIds.length);
    let receiverIdx = Math.floor(Math.random() * userIds.length);
    while (receiverIdx === senderIdx) {
      receiverIdx = Math.floor(Math.random() * userIds.length);
    }
    fromUserId = userIds[senderIdx];
    toUserId = userIds[receiverIdx];
    amountPaise = 100 + Math.floor(Math.random() * 9901); // 100..10,000
  } else if (SCENARIO === 'hot_wallet') {
    let senderIdx = Math.floor(Math.random() * userIds.length);
    while (userIds[senderIdx] === hotUser) {
      senderIdx = Math.floor(Math.random() * userIds.length);
    }
    fromUserId = userIds[senderIdx];
    toUserId = hotUser;
    amountPaise = 100 + Math.floor(Math.random() * 901); // 100..1,000
  } else if (SCENARIO === 'pingpong') {
    const isEven = exec.scenario.iterationInTest % 2 === 0;
    fromUserId = isEven ? userIds[0] : userIds[1];
    toUserId = isEven ? userIds[1] : userIds[0];
    amountPaise = 100;
  }

  const vu = exec.vu.idInTest;
  const iter = exec.scenario.iterationInTest;
  const rand = Math.floor(Math.random() * 10000000);
  const key = `${runId}-${vu}-${iter}-${rand}`.substring(0, 64);

  const res = http.post(
    `${BASE_URL}/api/v1/transfers`,
    JSON.stringify({
      fromUserId: fromUserId,
      toUserId: toUserId,
      amountPaise: amountPaise,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': key,
      },
      responseCallback: http.expectedStatuses(201, 409, 422),
    }
  );

  if (res.status === 201) {
    transfers_201.add(1);
  } else if (res.status === 422) {
    transfers_422.add(1);
  } else {
    transfers_other.add(1);
  }
}

