const NOTIFICATIONS_CHANGED = "gole:notifications-changed";

/** 읽음 저장이 끝난 계정의 벨만 다시 조회한다. 목록에서 이동한 뒤에도 전달된다. */
export function notifyNotificationsChanged(accountId: string): void {
  window.dispatchEvent(new CustomEvent(NOTIFICATIONS_CHANGED, { detail: accountId }));
}

export function subscribeNotificationsChanged(accountId: string, onChange: () => void): () => void {
  const listener = (event: Event) => {
    if ((event as CustomEvent<unknown>).detail === accountId) onChange();
  };
  window.addEventListener(NOTIFICATIONS_CHANGED, listener);
  return () => window.removeEventListener(NOTIFICATIONS_CHANGED, listener);
}
