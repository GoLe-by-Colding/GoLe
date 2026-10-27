import { MAKERS, MAKER_BLOG_URL } from "@shared/config";

export const dynamic = "force-static";

export function GET() {
  const team = MAKERS.map(
    (maker) =>
      `${maker.role}: ${maker.name} (${maker.handle})\nGitHub: ${maker.github}` +
      (maker.handle === "kscold" ? `\nBlog: ${MAKER_BLOG_URL}` : ""),
  ).join("\n\n");
  return new Response(`/* TEAM */\n${team}\n\n/* SITE */\nGoLe\nLanguage: 한국어\n`, {
    headers: { "Content-Type": "text/plain; charset=utf-8" },
  });
}
