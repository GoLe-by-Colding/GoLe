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
