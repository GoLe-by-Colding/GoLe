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

  // Spring MVC GET endpoints support HEAD. Keep a GET fallback so a future upstream that
  // explicitly rejects HEAD does not silently bring soft 404s back.
  if (response.status === 405 || response.status === 501) {
    return fetch(url, { ...options, method: "GET" });
  }
  return response;
}

/**
 * Next 16 streams every route below `(main)/loading.tsx`, so page-level notFound() cannot
 * reliably change an already-sent 200. Probe only public detail routes before rendering and
 * rewrite only an explicit upstream 404. A timeout, network failure, or 5xx continues to the
 * page so the application error boundary owns the outage instead of misreporting it as missing.
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
