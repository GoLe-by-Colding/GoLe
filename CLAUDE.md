@AGENTS.md

## Claude Code 전용

위 `@AGENTS.md`가 이 저장소의 정본 가이드다. **규칙을 추가하거나 고칠 때는 `AGENTS.md`를
고친다.** 이 파일에는 Claude Code에서만 의미가 있는 것만 둔다.

- 하네스(훅·권한)는 `.claude/settings.json`에 있고 **저장소에 추적된다.** 고치면 팀 전원에게
  간다. 개인 설정은 `.claude/settings.local.json`(추적 안 함)에 둔다.
- `.claude/settings.json`이 막는 것: `orca worktree create/rm`, `git push --force`,
  `git push origin main`. 워크트리·릴리스 브랜치는 사람이 판단한다.
- Stop 훅이 "이 세션에서 띄운 dev 서버가 남았다"며 턴을 막으면, 끄고 나서 마무리한다.
  Orca 터미널에 띄운 서버는 표시하지 않으므로 막히지 않는다.
- Stop 훅이 "오늘자 개발일지가 없다"며 막으면, 볼트에 오늘자 일지를 쓰고 마무리한다.
  `git push`·`gh pr create`·`gh pr merge` 를 한 세션에만 걸린다 — `AGENTS.md` 의
  "개발일지 — 나간 작업은 일지를 남긴다" 참고.
