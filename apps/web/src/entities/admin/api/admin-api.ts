import { apiRequest } from "@shared/api";
import type { PaymentMethod } from "@shared/lib";

// ── 타입 ─────────────────────────────────────────────────────

export interface AdminOverview {
  readonly counts: Readonly<Record<string, number>>;
  readonly gmv: number;
  readonly ordersByStatus: Readonly<Record<string, number>>;
  readonly activeListings: number;
  readonly pendingReports: number;
  readonly pendingSettlements: number;
  /** 롤링 배포 중 구버전 API에는 없을 수 있다. */
  readonly paymentReadiness?: AdminPaymentReadiness;
}

export interface AdminPaymentConfigurationIssue {
  readonly setting: string;
  readonly problem: "MISSING" | "INVALID";
}

export interface AdminPaymentReadiness {
  readonly enabled: boolean;
  readonly ready: boolean;
  readonly state: "DISABLED" | "MISCONFIGURED" | "READY";
  readonly channelType: "TEST" | "LIVE" | "UNKNOWN";
  /** 지금 열려 있는 결제수단. 롤링 배포 중 구버전 API에는 없을 수 있다. */
  readonly methods?: readonly string[];
  readonly currency: "KRW";
  readonly issues: readonly AdminPaymentConfigurationIssue[];
}

export interface AdminOrder {
  readonly id: string;
  readonly status: string;
  readonly amount: number;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly catalogSetNumber: string | null;
  /** 결제 승인 시 PG가 알려준 결제수단. 결제 전 주문은 null. */
  readonly paymentMethod: PaymentMethod | null;
  readonly createdAt: string | null;
}

export interface PaymentReconciliation {
  readonly orderId: string;
  readonly status: string;
}

export interface AdminSettlement {
  readonly orderId: string;
  readonly sellerId: string;
  readonly grossAmount: number;
  readonly fee: number;
  readonly payout: number;
  readonly feeRate: number;
  readonly status: "PENDING" | "PAID";
  readonly paymentReference: string | null;
  readonly createdAt: string | null;
  readonly paidAt: string | null;
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
  readonly featured: boolean;
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
  return token.length > 0 ? { Authorization: `Bearer ${token}` } : {};
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
  query?: string,
): Promise<readonly AdminOrder[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status) params.set("status", status);
  if (query?.trim()) params.set("q", query.trim());
  return get<readonly AdminOrder[]>(token, `/api/admin/orders?${params}`);
}

export function reconcileAdminOrderPayment(
  token: string,
  orderId: string,
): Promise<PaymentReconciliation> {
  return post<PaymentReconciliation>(token, `/api/admin/orders/${orderId}/reconcile-payment`);
}

export function fetchAdminSettlements(
  token: string,
  limit = 30,
  status?: AdminSettlement["status"],
): Promise<readonly AdminSettlement[]> {
  const query = status ? `&status=${status}` : "";
  return get<readonly AdminSettlement[]>(token, `/api/admin/settlements?limit=${limit}${query}`);
}

export function markAdminSettlementPaid(
  token: string,
  orderId: string,
  paymentReference: string,
): Promise<AdminSettlement> {
  return post<AdminSettlement>(token, `/api/admin/settlements/${orderId}/paid`, {
    paymentReference,
  });
}

// ── 매물 ─────────────────────────────────────────────────────

export function fetchAdminListings(
  token: string,
  limit = 30,
  status?: string,
  query?: string,
): Promise<readonly AdminListing[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status) params.set("status", status);
  if (query?.trim()) params.set("q", query.trim());
  return get<readonly AdminListing[]>(token, `/api/admin/listings?${params}`);
}

export function takedownListing(token: string, listingId: string, reason: string): Promise<void> {
  return post<void>(token, `/api/admin/listings/${listingId}/takedown`, { reason });
}

// ── 커뮤니티 ─────────────────────────────────────────────────

export function fetchAdminPosts(
  token: string,
  limit = 30,
  status?: string,
  query?: string,
): Promise<readonly AdminPost[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status) params.set("status", status);
  if (query?.trim()) params.set("q", query.trim());
  return get<readonly AdminPost[]>(token, `/api/admin/posts?${params}`);
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

export function resolveAdminReportTarget(
  token: string,
  reportId: string,
  reason: string,
): Promise<AdminReport> {
  return post<AdminReport>(token, `/api/admin/reports/${reportId}/resolve-target`, { reason });
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

// ── 예외 큐 (shipping-and-fees R7.6) ─────────────────────────

export interface AdminShipmentFacts {
  readonly carrierLabel: string;
  readonly waybillNumber: string;
  readonly status: string;
  readonly rawStatus: string | null;
  readonly registeredAt: string;
  readonly deliveredAt: string | null;
  readonly lastTrackedAt: string | null;
}

export interface AdminExceptionEntry {
  readonly type: string;
  readonly typeLabel: string;
  readonly orderId: string;
  readonly orderStatus: string;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly amount: number;
  readonly since: string;
  readonly reason: string | null;
  readonly disputeDetail: string | null;
  /** 배송 사실(R4.3) — 분쟁 판정의 객관적 근거. 미발송이면 null. */
  readonly shipment: AdminShipmentFacts | null;
}

export function fetchAdminExceptionQueue(token: string): Promise<readonly AdminExceptionEntry[]> {
  return get<readonly AdminExceptionEntry[]>(token, "/api/admin/exception-queue");
}

export function resolveAdminDispute(
  token: string,
  orderId: string,
  resolution: "refund" | "complete",
  note: string,
): Promise<readonly AdminExceptionEntry[]> {
  return post<readonly AdminExceptionEntry[]>(
    token,
    `/api/admin/orders/${orderId}/dispute-resolution`,
    {
      resolution,
      note,
    },
  );
}

export interface AdminOrderContacts {
  readonly buyerPhone: string | null;
  readonly sellerPhone: string | null;
  readonly notice: string;
}

/** 전체 번호 열람 — 서버가 감사 로그를 남긴다(R8.5). */
export function fetchAdminOrderContacts(
  token: string,
  orderId: string,
): Promise<AdminOrderContacts> {
  return get<AdminOrderContacts>(token, `/api/admin/orders/${orderId}/contacts`);
}
