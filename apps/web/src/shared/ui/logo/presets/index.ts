import type { MascotPresetKey } from "@gole/core/mascot";
import { babyRound } from "./baby-round";
import { blocky } from "./blocky";
import { chubby } from "./chubby";
import { firstSketch } from "./first-sketch";
import { flatSilhouette } from "./flat-silhouette";
import { fountain } from "./fountain";
import { frontStudHead } from "./front-stud-head";
import { kawaiiBrick } from "./kawaii-brick";
import { sideBrick } from "./side-brick";
import { sideBrickOriginal } from "./side-brick-original";
import { tailUp } from "./tail-up";
import { threeStuds } from "./three-studs";
import type { MascotArt } from "./types";

export type { MascotArt, MascotViewBox } from "./types";

/** 기본 제공 마스코트 그림. 키는 `@gole/core/mascot`·서버 `MascotPresets`와 같다. */
export const MASCOT_ART: Readonly<Record<MascotPresetKey, MascotArt>> = {
  "side-brick": sideBrick,
  "side-brick-original": sideBrickOriginal,
  "baby-round": babyRound,
  "front-stud-head": frontStudHead,
  "tail-up": tailUp,
  "kawaii-brick": kawaiiBrick,
  chubby,
  "flat-silhouette": flatSilhouette,
  blocky,
  "three-studs": threeStuds,
  fountain,
  "first-sketch": firstSketch,
};
