"use client";

import { useEffect } from "react";
import { makerConsoleMessage } from "../model/console-message";

export function MakerCredit() {
  useEffect(() => {
    if (process.env.NODE_ENV === "test") return;
    const browser = window as Window & { __goleMakerCreditPrinted?: boolean };
    if (browser.__goleMakerCreditPrinted) return;
    browser.__goleMakerCreditPrinted = true;
    console.log(...makerConsoleMessage());
  }, []);
  return null;
}
