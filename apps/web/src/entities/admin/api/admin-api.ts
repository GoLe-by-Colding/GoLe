import { apiRequest } from "@shared/api";

export interface AdminOverview {
  readonly counts: Readonly<Record<string, number>>;
  readonly gmv: number;
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

function auth(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export function fetchAdminOverview(token: string): Promise<AdminOverview> {
  return apiRequest<AdminOverview>("/api/admin/overview", {
    cache: "no-store",
    headers: auth(token),
  });
}

export function fetchAdminSets(token: string): Promise<readonly AdminLegoSet[]> {
  return apiRequest<readonly AdminLegoSet[]>("/api/admin/catalog/sets", {
    cache: "no-store",
    headers: auth(token),
  });
}

export function createAdminSet(token: string, input: CreateSetInput): Promise<AdminLegoSet> {
  return apiRequest<AdminLegoSet>("/api/admin/catalog/sets", {
    method: "POST",
    headers: auth(token),
    body: input,
  });
}

export function fetchAdminOrders(token: string, limit = 30): Promise<readonly AdminOrder[]> {
  return apiRequest<readonly AdminOrder[]>(`/api/admin/orders?limit=${limit}`, {
    cache: "no-store",
    headers: auth(token),
  });
}

export function fetchAdminListings(token: string, limit = 30): Promise<readonly AdminListing[]> {
  return apiRequest<readonly AdminListing[]>(`/api/admin/listings?limit=${limit}`, {
    cache: "no-store",
    headers: auth(token),
  });
}

export function takedownListing(token: string, listingId: string): Promise<void> {
  return apiRequest<void>(`/api/admin/listings/${listingId}/takedown`, {
    method: "POST",
    headers: auth(token),
  });
}

export interface AdminPost {
  readonly id: string;
  readonly authorId: string;
  readonly content: string;
  readonly type: string;
  readonly status: string;
  readonly createdAt: string | null;
}

export interface AdminAccount {
  readonly id: string;
  readonly email: string;
  readonly role: string;
  readonly status: string;
  readonly lockedUntil: string | null;
}

export function fetchAdminPosts(token: string, limit = 30): Promise<readonly AdminPost[]> {
  return apiRequest<readonly AdminPost[]>(`/api/admin/posts?limit=${limit}`, {
    cache: "no-store",
    headers: auth(token),
  });
}

export function removeAdminPost(token: string, postId: string): Promise<void> {
  return apiRequest<void>(`/api/admin/posts/${postId}/remove`, {
    method: "POST",
    headers: auth(token),
  });
}

export function fetchAdminAccounts(token: string, limit = 30): Promise<readonly AdminAccount[]> {
  return apiRequest<readonly AdminAccount[]>(`/api/admin/accounts?limit=${limit}`, {
    cache: "no-store",
    headers: auth(token),
  });
}

export function lockAdminAccount(token: string, accountId: string): Promise<void> {
  return apiRequest<void>(`/api/admin/accounts/${accountId}/lock`, {
    method: "POST",
    headers: auth(token),
  });
}

export function unlockAdminAccount(token: string, accountId: string): Promise<void> {
  return apiRequest<void>(`/api/admin/accounts/${accountId}/unlock`, {
    method: "POST",
    headers: auth(token),
  });
}
