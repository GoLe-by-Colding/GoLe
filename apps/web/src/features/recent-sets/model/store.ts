import { parseRecentSets, type RecentSet } from "./history";

const STORAGE_KEY = "gole.recent-sets.v1";
const CHANGE_EVENT = "gole:recent-sets";

export function readRecentSets(): string | null {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function writeRecentSets(entries: readonly RecentSet[]): boolean {
  try {
    if (entries.length === 0) window.sessionStorage.removeItem(STORAGE_KEY);
    else
      window.sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify(parseRecentSets(JSON.stringify(entries))),
      );
    window.dispatchEvent(new Event(CHANGE_EVENT));
    return true;
  } catch {
    return false;
  }
}

export function subscribeRecentSets(onChange: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

export function serverRecentSets(): null {
  return null;
}
