"""운영 Dockerfile의 외부 베이스만 검사한다. 빌드나 레지스트리 조회는 하지 않는다."""

from pathlib import Path
import re
import sys


DIGEST_REFERENCE = re.compile(r"[^\s$@]+@sha256:[0-9a-f]{64}\Z")
STAGE_NAME = re.compile(r"[a-zA-Z][a-zA-Z0-9_.-]*\Z")


def instructions(source: str):
    """기본 escape 문자의 행 연결과 주석을 처리한다."""
    pending = ""
    start = 0
    for number, line in enumerate(source.splitlines(), 1):
        stripped = line.strip()
        if re.match(r"#\s*escape\s*=", stripped, re.IGNORECASE):
            if stripped.split("=", 1)[1].strip() != "\\":
                raise ValueError(f"line {number}: unsupported Dockerfile escape directive")
        if not stripped or stripped.startswith("#"):
            continue
        if not pending:
            start = number
        continued = stripped.endswith("\\")
        pending += stripped[:-1] if continued else stripped
        if not continued:
            yield start, pending
            pending = ""
    if pending:
        raise ValueError(f"line {start}: unfinished Dockerfile instruction")


def validate(source: str) -> None:
    stages: set[str] = set()
    count = 0
    for number, instruction in instructions(source):
        tokens = instruction.split()
        if tokens[0].upper() != "FROM":
            # heredoc 본문 속 FROM을 실제 스테이지 선언으로 오인하지 않는다.
            # 현재 운영 파일에는 heredoc이 없으며, 지원 추가 전에는 명시적으로 거절한다.
            if tokens[0].upper() in {"RUN", "COPY", "ADD"} and "<<" in instruction:
                raise ValueError(f"line {number}: unsupported Dockerfile heredoc")
            continue
        args = tokens[1:]
        if args and args[0].startswith("--platform=") and args[0] != "--platform=":
            args = args[1:]
        if (len(args) not in (1, 3) or args[0].startswith("--")
                or (len(args) == 3 and (args[1].upper() != "AS" or not STAGE_NAME.fullmatch(args[2])))):
            raise ValueError(f"line {number}: invalid FROM instruction")
        base = args[0]
        # 현재 줄의 별칭을 등록하기 전에 검사해 자기 참조·미래 별칭으로 우회하지 못하게 한다.
        if base.lower() not in stages and not DIGEST_REFERENCE.fullmatch(base):
            raise ValueError(f"line {number}: production Dockerfile base is not digest-pinned: {base}")
        if len(args) == 3:
            stages.add(args[2].lower())
        count += 1
    if not count:
        raise ValueError("Dockerfile has no FROM instruction")


def main(paths: list[str]) -> int:
    if not paths:
        print("Usage: dockerfile_pins.py <Dockerfile> [...]", file=sys.stderr)
        return 1
    for value in paths:
        path = Path(value)
        try:
            validate(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            print(f"FAIL: {path}: {error}", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
