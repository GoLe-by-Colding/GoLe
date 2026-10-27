import { NextResponse, type NextRequest } from "next/server.js";

const PROBE_TIMEOUT_MS = 2_000;
const INTERNAL_NOT_FOUND_PATH = "/__gole_resource_not_found__";

const RESOURCE_API_PREFIX = {
  sets: "/api/v1/catalog/sets/",
  listings: "/api/v1/listings/",
  community: "/api/v1/community/posts/",
} as const;

type ResourceKind = keyof typeof RESOURCE_API_PREFIX;

interface ResourceRoute {
  readonly kind: ResourceKind;
  readonly identifier: string;
}

function matchResourceRoute(pathname: string): ResourceRoute | null {
  const match = /^\/(sets|listings|community)\/([^/]+)\/?$/.exec(pathname);
  if (match === null) return null;

  const [, kind, identifier] = match;
  if (kind === undefined || identifier === undefined) return null;
  // `/community/new`는 게시글 ID가 아니라 글쓰기 정적 경로다.
  if (kind === "community" && identifier === "new") return null;
  return { kind: kind as ResourceKind, identifier };
}

function serverApiBaseUrl(): string {
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (configured !== undefined && configured.length > 0) {
    return configured.replace(/\/+$/, "");
  }
  return "http://localhost:8080";
}

async function probeResource(route: ResourceRoute): Promise<Response> {
  const url = `${serverApiBaseUrl()}${RESOURCE_API_PREFIX[route.kind]}${encodeURIComponent(route.identifier)}`;
  const options: RequestInit = {
    method: "HEAD",
    cache: "no-store",
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(PROBE_TIMEOUT_MS),
  };
  const response = await fetch(url, options);

  // Spring MVC의 GET 경로는 HEAD도 지원한다. 이후 상위 서버가 HEAD를 거부하더라도
  // 없는 리소스가 200으로 응답하지 않도록 GET 재시도를 유지한다.
  if (response.status === 405 || response.status === 501) {
    return fetch(url, { ...options, method: "GET" });
  }
  return response;
}

/**
 * Next 16은 `(main)/loading.tsx` 아래 경로를 스트리밍하므로 페이지의 notFound()만으로는
 * 이미 전송한 200을 확실히 바꿀 수 없다. 공개 상세 경로만 렌더링 전에 조회하고,
 * 상위 서버가 명시적으로 404를 반환한 경우에만 경로를 바꾼다. 시간 초과·통신 실패·5xx는
 * 페이지 오류 경계에서 처리하여 장애를 리소스 부재로 잘못 안내하지 않는다.
 */
export async function proxy(request: NextRequest): Promise<NextResponse> {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return NextResponse.next();
  }

  const route = matchResourceRoute(request.nextUrl.pathname);
  if (route === null) return NextResponse.next();

  try {
    const response = await probeResource(route);
    if (response.status !== 404) return NextResponse.next();
  } catch {
    return NextResponse.next();
  }

  const destination = request.nextUrl.clone();
  destination.pathname = INTERNAL_NOT_FOUND_PATH;
  destination.search = "";
  return NextResponse.rewrite(destination, { status: 404 });
}

export const config = {
  matcher: ["/sets/:path", "/listings/:path", "/community/:path"],
};
