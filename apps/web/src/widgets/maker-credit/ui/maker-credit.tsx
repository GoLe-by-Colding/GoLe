"use client";

import { useEffect } from "react";
import { makerConsoleMessage } from "../model/console-message";

export function MakerCredit() {
  useEffect(() => {
    if (process.env.NODE_ENV === "test") return;
    const browser = window as Window & { __goleMakerCreditPrinted?: boolean };
    if (browser.__goleMakerCreditPrinted) return;
    browser.__goleMakerCreditPrinted = true;
    // 운영의 일반 콘솔 제거 정책을 약화하지 않고 이스터에그 한 곳만 보존한다.
    // SWC가 지우는 직접 호출 대신 메서드를 바인딩하여 문서당 한 번 출력한다.
    const print = Function.prototype.bind.call(console.log, console);
    print(...makerConsoleMessage());
  }, []);
  return null;
}
