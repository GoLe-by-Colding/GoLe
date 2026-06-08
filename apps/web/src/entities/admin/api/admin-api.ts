import { apiRequest } from "@shared/api";

export interface AdminOverview {
  readonly counts: Readonly<Record<string, number>>;
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
