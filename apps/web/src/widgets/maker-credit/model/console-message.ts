import { MAKERS, MAKER_BLOG_URL, MAKER_CONTACT_URL } from "@shared/config";

/** 사용자 확정 원문. 공백과 줄바꿈을 포함한 글자 배치를 변경하지 않는다. */
export const COLDING_ART = `   ██████   ██████   ██       ██████   ██  ███    ██   ██████
  ██       ██    ██  ██       ██   ██  ██  ████   ██  ██
  ██       ██    ██  ██       ██   ██  ██  ██ ██  ██  ██   ███
  ██       ██    ██  ██       ██   ██  ██  ██  ██ ██  ██    ██
   ██████   ██████   ███████  ██████   ██  ██   ████   ██████
     ▀█        █▀       █       ▀█     █▀         █      ▀█
      ▀        ▀        ▀        ▀     ▀          ▀       ▀
                        ·                         ·`;

const ART_COLORS = [
  "#e0f2fe",
  "#bae6fd",
  "#7dd3fc",
  "#7dd3fc",
  "#38bdf8",
  "#0ea5e9",
  "#0284c7",
  "#64748b",
] as const;

export function makerConsoleMessage(): readonly [string, ...string[]] {
  const team = MAKERS.map(
    (maker) => `${maker.name} (${maker.handle}) · ${maker.role}\n${maker.github}`,
  ).join("\n");
  return [
    `${COLDING_ART.split("\n")
      .map((line) => `%c${line}`)
      .join("\n")}\n\n` +
      "%c🧊 Made by COLDING\n" +
      `%cGoLe를 만든 사람들\n${team}\nBlog: ${MAKER_BLOG_URL}\n\n` +
      `%c함께 일하고 싶거나 궁금한 게 있다면\n→ ${MAKER_CONTACT_URL}`,
    ...ART_COLORS.map(
      (color) =>
        `color:${color};font-family:Menlo,Consolas,monospace;font-size:9px;line-height:1;font-weight:normal;white-space:pre;`,
    ),
    "color:#38bdf8;font-weight:bold;font-size:14px;line-height:1.5;font-family:monospace;",
    "color:inherit;font-weight:normal;font-size:12px;line-height:1.5;font-family:monospace;",
    "color:#7dd3fc;font-weight:normal;font-size:12px;line-height:1.5;font-family:monospace;",
  ];
}
