"""실제 git 저장소에 커밋을 만들어 릴리스 스캐너가 보는 모양을 재현한다."""

import subprocess
from pathlib import Path


def git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=str(repo), capture_output=True, text=True, check=True
    ).stdout.strip()


def commit(repo: Path, subject: str, *, web: bool) -> str:
    target = repo / ("apps/web/src/app/page.tsx" if web else "docs/note.md")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(f"// {subject}\n", encoding="utf-8")
    git(repo, "add", "-A")
    git(
        repo,
        "-c",
        "user.email=test@gole.test",
        "-c",
        "user.name=test",
        "commit",
        "-q",
        "-m",
        subject,
    )
    return git(repo, "rev-parse", "HEAD")
