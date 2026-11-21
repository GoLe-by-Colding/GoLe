// 검색·세트 SSR 실패·재시도 전용 읽기 서버. 실제 DB에 쓰지 않고 나머지 GET만 개발 API로 전달한다.
// 실행: node tests-e2e/support/search-api-fixture.mjs (웹 패키지 기준)
import { createServer } from "node:http";

const port = Number(process.env.SEARCH_FIXTURE_PORT ?? "8093");
const upstream = process.env.SEARCH_FIXTURE_UPSTREAM ?? "http://localhost:8090";
const seen = new Set();
const setReads = new Map();
const listing = {
  id: "search-fixture",
  sellerId: "fixture-seller",
  title: "다시 찾은 브릭 세트",
  description: "검색 회귀 검증용",
  price: 30000,
  condition: "used_good",
  completeness: "no_box",
  hasBox: false,
  hasManual: true,
  hasMissingParts: true,
  missingPartsNote: "검증용 부품 누락",
  defectsNote: "",
  photoUrls: [],
  catalogSetNumber: null,
  category: "set",
  interestTag: null,
  status: "active",
  createdAt: "2026-10-24T00:00:00Z",
};

createServer(async (request, response) => {
  response.setHeader("Access-Control-Allow-Origin", "http://localhost:3003");
  response.setHeader("Access-Control-Allow-Credentials", "true");
  response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (request.method === "OPTIONS") {
    response.writeHead(204).end();
    return;
  }
  const send = (status, data) => {
    response.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" });
    response.end(JSON.stringify(data));
  };
  if (request.method !== "GET") {
    send(405, { message: "읽기 전용 테스트 서버" });
    return;
  }
  const url = new URL(request.url ?? "/", "http://localhost");
  const query = url.searchParams.get("query") ?? "";
  const catalogSetNumber = url.pathname.match(/^\/api\/v1\/catalog\/sets\/(9\d{6})$/)?.[1];
  if (catalogSetNumber !== undefined) {
    send(200, {
      setNumber: catalogSetNumber,
      name: "조회 복구 검증 세트",
      theme: "Icons",
      pieceCount: 100,
      releaseYear: 2024,
      retirementStatus: "active",
      imageUrl: null,
    });
    return;
  }
  const setNumber = url.searchParams.get("setNumber") ?? "";
  if (url.pathname === "/api/v1/listings" && /^9\d{6}$/.test(setNumber)) {
    const reads = (setReads.get(setNumber) ?? 0) + 1;
    setReads.set(setNumber, reads);
    if (reads <= 2) send(503, { code: "UNAVAILABLE", message: "세트 매물 조회 일시 오류" });
    else send(200, []);
    return;
  }
  if (url.pathname === "/api/v1/listings" && query.startsWith("__e2e_retry_")) {
    if (!seen.has(query)) {
      seen.add(query);
      send(503, { code: "UNAVAILABLE", message: "재시도 검증용 일시 오류" });
    } else send(200, [listing]);
    return;
  }
  if (url.pathname === "/api/v1/listings" && query === "__e2e_card_states") {
    send(
      200,
      ["active", "reserved", "sold", "deleted"].map((status) => ({
        ...listing,
        id: `fixture-${status}`,
        title: `브릭 ${status}`,
        status,
      })),
    );
    return;
  }
  if (url.pathname === "/api/v1/listings" && query === "__e2e_non_set_cards") {
    send(
      200,
      ["parts", "minifig", "moc"].map((category) => ({
        ...listing,
        id: `fixture-${category}`,
        title: `출처 세트 ${category}`,
        catalogSetNumber: "10307",
        category,
      })),
    );
    return;
  }
  if (url.pathname.startsWith("/api/v1/catalog") && query.startsWith("__e2e_")) {
    send(200, []);
    return;
  }
  try {
    // 세션 헤더·쿠키를 전달하지 않는다. 공개 읽기만 사용한다.
    const result = await fetch(new URL(request.url ?? "/", upstream), {
      signal: AbortSignal.timeout(5000),
    });
    response.writeHead(result.status, {
      "Content-Type": result.headers.get("content-type") ?? "application/json",
    });
    response.end(Buffer.from(await result.arrayBuffer()));
  } catch {
    send(503, { message: "개발 API를 확인해 주세요." });
  }
}).listen(port, "127.0.0.1", () => console.log(`검색 읽기 검증 서버: ${port}`));
