export interface RecentSet {
  readonly setNumber: string;
  readonly name: string;
}

export const RECENT_SET_LIMIT = 8;

function isRecentSet(value: unknown): value is RecentSet {
  if (typeof value !== "object" || value === null) return false;
  const entry = value as Record<string, unknown>;
  return (
    typeof entry["setNumber"] === "string" &&
    /^\d{1,10}(?:-\d{1,3})?$/.test(entry["setNumber"]) &&
    typeof entry["name"] === "string" &&
    entry["name"].trim().length > 0 &&
    entry["name"].length <= 300
  );
}

/** 외부에서 변조하거나 구버전이 남긴 저장 데이터도 신뢰하지 않는다. */
export function parseRecentSets(raw: string | null): readonly RecentSet[] {
  if (raw === null || raw.length > 32_768) return [];
  try {
    const value: unknown = JSON.parse(raw);
    if (!Array.isArray(value)) return [];
    const seen = new Set<string>();
    return value
      .filter(isRecentSet)
      .filter((entry) => {
        if (seen.has(entry.setNumber)) return false;
        seen.add(entry.setNumber);
        return true;
      })
      .slice(0, RECENT_SET_LIMIT)
      .map(({ setNumber, name }) => ({ setNumber, name }));
  } catch {
    return [];
  }
}

export function visitRecentSet(
  entries: readonly RecentSet[],
  entry: RecentSet,
): readonly RecentSet[] {
  if (!isRecentSet(entry)) return entries;
  return [
    { setNumber: entry.setNumber, name: entry.name },
    ...entries.filter((item) => item.setNumber !== entry.setNumber),
  ].slice(0, RECENT_SET_LIMIT);
}
