"""수동 단발 검증: 실제 Java 어댑터와 Python 서버를 연결하며 유료 API는 호출하지 않는다."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path[:0] = [str(ROOT / "apps/support-agent/src"), str(ROOT / "apps/support-agent/generated")]

from gole_brick_filter.grpc_server import create_server


class FakeEditor:
    def __init__(self):
        self.modes = []

    def edit(self, image, mode):
        self.modes.append(mode)
        return image


def main():
    editor = FakeEditor()
    server, port = create_server(editor, "interop-test-token-not-a-real-secret-1234", 0)
    server.start()
    try:
        wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
        subprocess.run([
            str(ROOT / "apps/api" / wrapper), "-p", str(ROOT / "apps/api"),
            "-I", str(ROOT / ".kiro/specs/agent-worker/brick-interop.init.gradle"),
            "brickGrpcInterop", f"-PbrickInteropTarget=127.0.0.1:{port}",
        ], cwd=ROOT, check=True, timeout=180)
        assert sorted(editor.modes) == ["BRICK_OBJECT", "MINIFIGURE"], editor.modes
        print("실제 Java/Python 두 모드 각 1회 호출 확인, 외부 API 호출 없음")
    finally:
        server.stop(0).wait()


if __name__ == "__main__":
    main()
