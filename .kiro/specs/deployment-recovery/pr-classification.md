# 큰 릴리스 PR 자동 분류 — 2026-09-24

## 요구사항

- 300개가 넘는 파일을 바꾸는 릴리스 PR도 모든 변경 경로로 라벨을 계산한다.
- 기존 담당자와 제목·브랜치·대상별 라벨 정책은 유지한다.

## 설계

PR #151은 374개 파일을 포함해 `gh pr diff --name-only`가 GitHub HTTP 406으로 실패했다.
REST `pulls/{number}/files` API에 `--paginate`와 페이지 크기 100을 적용해 파일명만 읽는다.
라벨·담당자 쓰기 로직은 변경하지 않는다.

## 검증

- 실제 PR #151의 API 374개 파일과 로컬 main…dev 374개 경로가 누락·초과 없이 일치함.
- CI와 동일한 digest의 actionlint·ShellCheck 전체 워크플로 검사를 통과함.
