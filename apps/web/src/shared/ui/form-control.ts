/** 입력 종류가 달라도 초점·오류·비활성 상태는 같은 문법으로 보여 준다. */
export const FORM_CONTROL_BASE =
  "w-full min-w-0 rounded-lg border bg-white px-3 text-base text-neutral-900 placeholder:text-neutral-500 transition-colors hover:border-neutral-400 focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:border-neutral-200 disabled:bg-neutral-100 disabled:text-neutral-500";

export function formControlState(invalid: boolean): string {
  return invalid
    ? "border-danger hover:border-danger focus-visible:outline-danger"
    : "border-neutral-300 focus-visible:border-brand-600 focus-visible:outline-brand-600";
}
