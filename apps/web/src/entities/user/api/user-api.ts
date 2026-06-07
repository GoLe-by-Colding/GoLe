import { apiRequest } from "@shared/api";
import type { RegisterResult, Session } from "../model/types";

export function registerAccount(
  email: string,
  password: string,
): Promise<RegisterResult> {
  return apiRequest<RegisterResult>("/api/v1/accounts", {
    method: "POST",
    body: { email, password },
  });
}

export function verifyEmail(email: string, code: string): Promise<void> {
  return apiRequest<void>("/api/v1/accounts/verification", {
    method: "POST",
    body: { email, code },
  });
}

export function signIn(email: string, password: string): Promise<Session> {
  return apiRequest<Session>("/api/v1/accounts/sessions", {
    method: "POST",
    body: { email, password },
  });
}
