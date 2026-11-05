// 헌법 원칙 IV 응답 시간 예산 검증(T119). perf/seed.sql로 기준 데이터셋을 넣은 뒤 실행한다.
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 -v "$PWD/perf:/scripts" grafana/k6 run /scripts/budget.js
// 예산(p95): 단건 200ms, 목록·검색 300ms, 쓰기 500ms. perf/baseline.json 값이 있으면 그 1.2배와 예산 중 작은 값을 쓴다.
import http from "k6/http";
import { check, fail } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const PASSWORD = __ENV.OWNER_PASSWORD || "perf-owner-password-1234";
const BUDGET = { single: 200, list: 300, write: 500 };
const baseline = JSON.parse(open("./baseline.json"));

function limit(type) {
  const base = baseline[type];
  return base ? Math.min(BUDGET[type], Math.round(base * 1.2)) : BUDGET[type];
}

export const options = {
  // 반복마다 쿠키를 지우면 로그인 세션이 사라져 로그인 화면(200)을 재게 된다.
  noCookiesReset: true,
  scenarios: {
    budget: { executor: "constant-vus", vus: 5, duration: __ENV.DURATION || "60s" },
  },
  thresholds: {
    "http_req_duration{type:single}": [`p(95)<${limit("single")}`],
    "http_req_duration{type:list}": [`p(95)<${limit("list")}`],
    "http_req_duration{type:write}": [`p(95)<${limit("write")}`],
    checks: ["rate>0.99"],
  },
};

function csrf(html) {
  const field = html.match(/name="_csrf" value="([^"]+)"/);
  const meta = html.match(/name="csrf-token" content="([^"]+)"/);
  return field ? field[1] : meta ? meta[1] : null;
}

/** 빈 DB면 소유자를 만든다(이미 있으면 /setup은 404). */
export function setup() {
  const page = http.get(`${BASE}/setup`, { redirects: 0 });
  if (page.status === 200) {
    const res = http.post(`${BASE}/setup`, { password: PASSWORD, passwordConfirm: PASSWORD, _csrf: csrf(page.body) });
    if (res.status >= 400) {
      fail(`소유자 설정 실패: ${res.status}`);
    }
  }
}

let ids = null;

function login() {
  const page = http.get(`${BASE}/login`);
  const res = http.post(`${BASE}/login`, { username: "owner", password: PASSWORD, _csrf: csrf(page.body) });
  if (!res.url.endsWith("/") || res.status !== 200) {
    fail(`로그인 실패: ${res.status} ${res.url}`);
  }
  const holding = res.body.match(/id="holding-(\d+)"/);
  const security = res.body.match(/href="\/securities\/(\d+)\/guides"/);
  const history = http.get(`${BASE}/securities/${security[1]}/guides`);
  const guide = history.body.match(/href="\/guides\/(\d+)"/);
  return {
    holding: holding[1],
    guide: guide[1],
    csrf: csrf(res.body),
    header: (res.body.match(/name="csrf-header" content="([^"]+)"/) || [])[1] || "X-CSRF-TOKEN",
  };
}

export default function () {
  if (!ids) {
    ids = login();
  }
  const htmx = { headers: { "HX-Request": "true" } };

  check(http.get(`${BASE}/`, { tags: { type: "list" } }), {
    "dashboard 200": (r) => r.status === 200 && r.body.includes('id="holding-'),
  });
  check(http.get(`${BASE}/fragments/quotes`, { ...htmx, tags: { type: "list" } }), {
    "quotes 200": (r) => r.status === 200,
  });
  check(http.get(`${BASE}/securities/search?q=${encodeURIComponent("성능종목1")}`, { ...htmx, tags: { type: "list" } }), {
    "search 200": (r) => r.status === 200,
  });
  check(http.get(`${BASE}/guides/${ids.guide}`, { tags: { type: "single" } }), {
    "guide 200": (r) => r.status === 200 && r.body.includes("guide-card"),
  });

  const headers = { "HX-Request": "true", [ids.header]: ids.csrf };
  const price = 50000 + Math.floor(Math.random() * 1000);
  check(
    http.put(`${BASE}/holdings/${ids.holding}`, { avgPrice: String(price), quantity: "10" }, { headers, tags: { type: "write" } }),
    { "update 200": (r) => r.status === 200 }
  );
}
