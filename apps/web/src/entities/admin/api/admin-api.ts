import { apiRequest } from "@shared/api";

// ── 타입 ─────────────────────────────────────────────────────

export interface AdminOverview {
  readonly counts: Readonly<Record<string, number>>;
  /** 완료 주문 거래액. 플랫폼을 통과한 돈이지 플랫폼의 돈이 아니다. */
  readonly gmv: number;
  /** 완료 주문의 수수료 합계 — 실제 플랫폼 매출. */
  readonly platformRevenue: number;
  readonly ordersByStatus: Readonly<Record<string, number>>;
  readonly activeListings: number;
}

export interface AdminOrder {
  readonly id: string;
  readonly status: string;
  readonly amount: number;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly catalogSetNumber: string | null;
  /** 정산 값은 미정산 주문에서 null이다. 0으로 오지 않으므로 "수수료 0원"과 구분된다. */
  readonly fee: number | null;
  readonly payout: number | null;
  readonly feeRate: number | null;
  readonly createdAt: string | null;
}

export interface AdminListing {
  readonly id: string;
  readonly title: string;
  readonly sellerId: string;
  readonly price: number;
  readonly status: string;
  readonly category: string | null;
  readonly createdAt: string | null;
}

export interface AdminPost {
  readonly id: string;
  readonly authorId: string;
  readonly content: string;
  readonly type: string;
  readonly status: string;
  readonly createdAt: string | null;
}

export type AdminRole = "USER" | "ADMIN";
export type AdminAccountStatus = "UNVERIFIED" | "VERIFIED" | "SUSPENDED";

export interface AdminAccount {
  readonly id: string;
  readonly email: string;
  readonly role: AdminRole;
  readonly status: AdminAccountStatus;
  readonly lockedUntil: string | null;
  readonly suspendedReason: string | null;
}

export interface AdminReport {
  readonly id: string;
  readonly reporterId: string;
  readonly targetType: "LISTING" | "POST";
  readonly targetId: string;
  readonly reason: string;
  readonly detail: string;
  readonly status: "PENDING" | "RESOLVED" | "DISMISSED";
  readonly createdAt: string | null;
  readonly handledAt: string | null;
}

export interface AdminLegoSet {
  readonly setNumber: string;
  readonly name: string;
  readonly theme: string;
  readonly pieceCount: number;
  readonly releaseYear: number;
  readonly retirementStatus: string;
  readonly imageUrl: string | null;
}

export interface CreateSetInput {
  readonly setNumber: string;
  readonly name: string;
  readonly theme: string;
  readonly pieceCount: number;
  readonly releaseYear: number;
  readonly retirementStatus: "ACTIVE" | "RETIRED";
  readonly imageUrl: string;
  readonly featured: boolean;
}

export type UpdateSetInput = Omit<CreateSetInput, "setNumber">;

/** 감사 로그 한 줄. 조치자 이메일은 조치 시점 스냅샷이다. */
export interface AdminAuditEntry {
  readonly id: string;
  readonly actorId: string;
  readonly actorEmail: string;
  readonly type: string;
  readonly targetType: string;
  readonly targetId: string;
  readonly reason: string | null;
  readonly occurredAt: string | null;
}

// ── 공통 ─────────────────────────────────────────────────────

function auth(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

/** 운영 목록은 항상 조회 시점 데이터여야 한다(요구사항 2.5). */
function get<T>(token: string, path: string): Promise<T> {
  return apiRequest<T>(path, { cache: "no-store", headers: auth(token) });
}

function post<T>(token: string, path: string, body?: unknown): Promise<T> {
  return apiRequest<T>(path, { method: "POST", headers: auth(token), body });
}

// ── 대시보드 · 감사 ───────────────────────────────────────────

export function fetchAdminOverview(token: string): Promise<AdminOverview> {
  return get<AdminOverview>(token, "/api/admin/overview");
}

export function fetchAdminAudit(token: string, limit = 50): Promise<readonly AdminAuditEntry[]> {
  return get<readonly AdminAuditEntry[]>(token, `/api/admin/audit?limit=${limit}`);
}

// ── 주문 ─────────────────────────────────────────────────────

export function fetchAdminOrders(
  token: string,
  limit = 30,
  status?: string,
): Promise<readonly AdminOrder[]> {
  const query = status ? `&status=${encodeURIComponent(status)}` : "";
  return get<readonly AdminOrder[]>(token, `/api/admin/orders?limit=${limit}${query}`);
}

// ── 매물 ─────────────────────────────────────────────────────

export function fetchAdminListings(token: string, limit = 30): Promise<readonly AdminListing[]> {
  return get<readonly AdminListing[]>(token, `/api/admin/listings?limit=${limit}`);
}

export function takedownListing(token: string, listingId: string, reason: string): Promise<void> {
  return post<void>(token, `/api/admin/listings/${listingId}/takedown`, { reason });
}

// ── 커뮤니티 ─────────────────────────────────────────────────

export function fetchAdminPosts(token: string, limit = 30): Promise<readonly AdminPost[]> {
  return get<readonly AdminPost[]>(token, `/api/admin/posts?limit=${limit}`);
}

export function removeAdminPost(token: string, postId: string, reason: string): Promise<void> {
  return post<void>(token, `/api/admin/posts/${postId}/remove`, { reason });
}

// ── 신고 ─────────────────────────────────────────────────────

export function fetchAdminReports(
  token: string,
  limit = 30,
  status?: AdminReport["status"],
): Promise<readonly AdminReport[]> {
  const query = status ? `&status=${status}` : "";
  return get<readonly AdminReport[]>(token, `/api/admin/reports?limit=${limit}${query}`);
}

export function resolveAdminReport(token: string, reportId: string): Promise<AdminReport> {
  return post<AdminReport>(token, `/api/admin/reports/${reportId}/resolve`);
}

export function dismissAdminReport(token: string, reportId: string): Promise<AdminReport> {
  return post<AdminReport>(token, `/api/admin/reports/${reportId}/dismiss`);
}

// ── 회원 ─────────────────────────────────────────────────────

export function fetchAdminAccounts(
  token: string,
  limit = 30,
  query?: string,
): Promise<readonly AdminAccount[]> {
  const search = query ? `&q=${encodeURIComponent(query)}` : "";
  return get<readonly AdminAccount[]>(token, `/api/admin/accounts?limit=${limit}${search}`);
}

export function suspendAdminAccount(
  token: string,
  accountId: string,
  reason: string,
): Promise<AdminAccount> {
  return post<AdminAccount>(token, `/api/admin/accounts/${accountId}/suspend`, { reason });
}

export function reinstateAdminAccount(token: string, accountId: string): Promise<AdminAccount> {
  return post<AdminAccount>(token, `/api/admin/accounts/${accountId}/reinstate`);
}

export function changeAdminAccountRole(
  token: string,
  accountId: string,
  role: AdminRole,
): Promise<AdminAccount> {
  return post<AdminAccount>(token, `/api/admin/accounts/${accountId}/role`, { role });
}

// ── 카탈로그 ─────────────────────────────────────────────────

export function fetchAdminSets(token: string): Promise<readonly AdminLegoSet[]> {
  return get<readonly AdminLegoSet[]>(token, "/api/admin/catalog/sets");
}

export function createAdminSet(token: string, input: CreateSetInput): Promise<AdminLegoSet> {
  return post<AdminLegoSet>(token, "/api/admin/catalog/sets", input);
}

export function updateAdminSet(
  token: string,
  setNumber: string,
  input: UpdateSetInput,
): Promise<AdminLegoSet> {
  return post<AdminLegoSet>(token, `/api/admin/catalog/sets/${setNumber}`, input);
}

export function setAdminSetFeatured(
  token: string,
  setNumber: string,
  featured: boolean,
): Promise<AdminLegoSet> {
  return post<AdminLegoSet>(token, `/api/admin/catalog/sets/${setNumber}/featured`, { featured });
}
