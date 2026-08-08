export function readSessionAuthorization(): Readonly<Record<string, string>> {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem("gole.session");
    const session = raw === null ? null : (JSON.parse(raw) as { sessionToken?: unknown });
    return typeof session?.sessionToken === "string" && session.sessionToken.length > 0
      ? { Authorization: `Bearer ${session.sessionToken}` }
      : {};
  } catch {
    return {};
  }
}
