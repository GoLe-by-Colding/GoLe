import { readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const APP_DIRECTORY = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../src/app",
);
const FORBIDDEN_ROUTE =
  /^\/(?:admin(?:\/|$)|auth(?:\/|$)|login$|signup$|forgot-password$|verify$|payments(?:\/|$))/u;

async function findPageFiles(directory: string): Promise<readonly string[]> {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map(async (entry) => {
      const entryPath = path.join(directory, entry.name);
      if (entry.isDirectory()) return findPageFiles(entryPath);
      return entry.isFile() && entry.name === "page.tsx" ? [entryPath] : [];
    }),
  );
  return nested.flat();
}

export function routeFromPageFile(pageFile: string): string | null {
  const relative = path.relative(APP_DIRECTORY, path.resolve(pageFile)).replaceAll("\\", "/");
  if (relative.startsWith("../") || (!relative.endsWith("/page.tsx") && relative !== "page.tsx")) {
    return null;
  }
  const directory = relative === "page.tsx" ? "" : relative.slice(0, -"/page.tsx".length);
  const segments = directory.split("/").filter((segment) => segment && !/^\(.+\)$/u.test(segment));
  if (segments.some((segment) => segment.startsWith("[") || segment.endsWith("]"))) return null;
  return segments.length === 0 ? "/" : `/${segments.join("/")}`;
}

export function isPublicCaptureRoute(route: string): boolean {
  return route.startsWith("/") && !route.startsWith("//") && !FORBIDDEN_ROUTE.test(route);
}

export async function listPublicRoutes(): Promise<readonly string[]> {
  const routes = (await findPageFiles(APP_DIRECTORY))
    .map(routeFromPageFile)
    .filter((route): route is string => route !== null && isPublicCaptureRoute(route));
  return [...new Set(routes)].sort();
}
