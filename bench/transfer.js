import http from 'k6/http';
import { check } from 'k6';
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

export function setup() {
  const runId = Math.random().toString(36).substring(2, 10);
  const userIds = [];

  for (let i = 0; i < USERS; i++) {
    const userRes = http.post(
      `${BASE_URL}/api/v1/users`,
      JSON.stringify({
        name: `bench-${runId}-${i}`,
        email: `bench-${runId}-${i}@example.com`,
      }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    check(userRes, { 'user created': (r) => r.status === 201 });
    const user = JSON.parse(userRes.body);
    userIds.push(user.userId);

    const topupKey = `bench-topup-${runId}-${i}`;
    const topupRes = http.post(
      `${BASE_URL}/api/v1/wallets/${user.userId}/topup`,
      JSON.stringify({ amountPaise: 10000000 }),
      {
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': topupKey,
        },
      }
    );
    check(topupRes, { 'topup succeeded': (r) => r.status === 201 });
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

