# Browser Dialogs Design

## `shared/ui/dialog` — `useDialog()`

```ts
const dialog = useDialog();
if (!(await dialog.confirm({ title, description, confirmLabel, tone: "danger" }))) return;
const id = await dialog.prompt({ title, label, validate: (v) => (v === requestId ? null : "…") });
const code = await dialog.prompt({ title, label, choices: [{ value, label }], defaultValue });
await dialog.alert({ title, description });
return <>{…}{dialog.element}</>;
```

- **화면마다 훅 하나**다. 전역 Provider를 두지 않아 레이아웃·다른 화면을 건드리지 않는다. `element`는 `createPortal`로
  `document.body`에 그려 표(`<td>`)·transform 조상 안에서도 화면 전체를 덮는다. 요청이 없으면 `null`이다.
- 상태는 "지금 열린 요청 하나"(`ref` + state). 열린 동안 오는 요청은 즉시 취소값(`false`/`null`)으로 끝난다(R1.4).
  언마운트하면 열린 요청을 취소값으로 끝내 `await`가 매달리지 않는다.
- 모달 본체는 요청마다 `key`를 바꿔 입력값·오류가 새로 시작한다. 초점 이동·Tab 가두기·Escape·초점 복귀·스크롤 잠금은
  기존 `ThirdPartyProvisionConsentDialog`의 방식을 그대로 따른다.
- 확인·안내는 `role="alertdialog"`, 입력은 `role="dialog"`. 제목은 `aria-labelledby`, 본문은 `aria-describedby`.
  본문 줄바꿈은 `whitespace-pre-line`으로 보인다(기존 `\n` 문구 보존).
- 입력은 `<form>`이라 Enter로 제출된다. `validate`가 문구를 돌려주면 `role="alert"`로 보이고 닫지 않는다.
  `choices`가 있으면 라디오 묶음이고, 고르지 않았으면 제출되지 않는다.
- 비동기 조치의 진행 중 표시는 호출부의 기존 busy 상태가 맡는다(모달은 결정만 받는다).

## 교체 목록 (14곳)

| 경로 | 종류 |
|---|---|
| `views/community-post` 댓글 삭제 | confirm |
| `views/admin-mascot` 업로드 에셋 지우기 | confirm |
| `views/account-security` 동의 철회 · 탈퇴 요청 · 접수 안내 | confirm · confirm · alert |
| `views/admin/settlements-view` 지급 완료 · 차단 복구 | confirm · confirm (실이체 문구 그대로) |
| `views/admin/account-deletions-view` 요청 ID 확인 · 보존 사유 · 파기 검토 | prompt(검증) · prompt(선택) · confirm |
| `features/part-request/manage` 마감 · 삭제 | confirm · confirm |
| `features/manage-post` 게시글 삭제 | confirm |
| `features/manage-listing/sell-to-bid` 입찰가 즉시 판매 | confirm |

## 회귀 방지

- `eslint.config.mjs`에 `no-alert: error`.
- E2E는 `page.on("dialog")` 대신 모달 버튼을 누르고, 기본 대화상자가 뜨면 실패하게 센다.
