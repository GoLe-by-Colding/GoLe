"""Claude Code 가 부르는 캡처 명령. 금지 화면은 프롬프트와 무관하게 코드에서 막혀야 한다."""

import json

import pytest

from gole_promotion_agent import capture_cli
from gole_promotion_agent.fakes import FakeCamera

ROUTES = ("/", "/listings", "/prices")


def _run(tmp_path, *args, demo="DEMO", monkeypatch):
    monkeypatch.setenv("PROMOTION_AGENT_DATA_SOURCE", demo)
    cameras = []

    def factory(allowed):
        cameras.append(FakeCamera(allowed))
        return cameras[-1]

    code = capture_cli.main(
        [*args, "--out", str(tmp_path)],
        routes=lambda is_demo: [r for r in ROUTES if not (is_demo and r == "/prices")],
        camera_factory=factory,
    )
    return code, cameras


def test_capture_writesPngAndMeta(tmp_path, monkeypatch):
    code, cameras = _run(
        tmp_path,
        "--label", "목록",
        "--route", "/listings",
        "--interactions", '[{"kind":"scroll","to":"bottom"}]',
        monkeypatch=monkeypatch,
    )

    assert code == 0
    (meta_file,) = tmp_path.glob("*.json")
    meta = json.loads(meta_file.read_text(encoding="utf-8"))
    assert meta["label"] == "목록"
    assert meta["route"] == "/listings"
    assert meta["interactions"] == [{"kind": "scroll", "to": "bottom"}]
    assert (tmp_path / meta["file"]).exists()
    assert cameras[0].calls[0][0] == "/listings"


def test_capture_rejectsPricesInDemo(tmp_path, monkeypatch):
    code, cameras = _run(tmp_path, "--label", "시세", "--route", "/prices", monkeypatch=monkeypatch)

    assert code == 2
    assert cameras == []
    assert list(tmp_path.iterdir()) == []


@pytest.mark.parametrize("route", ["/admin", "/profile", "https://evil.example", "/unknown"])
def test_capture_rejectsUnlistedRoutes(tmp_path, monkeypatch, route):
    code, cameras = _run(tmp_path, "--label", "x", "--route", route, monkeypatch=monkeypatch)

    assert code == 2
    assert cameras == []


def test_capture_rejectsBadInteractions(tmp_path, monkeypatch):
    code, _ = _run(
        tmp_path,
        "--label", "x",
        "--route", "/",
        "--interactions", '[{"kind":"type","text":"hi"}]',
        monkeypatch=monkeypatch,
    )

    assert code == 2


def test_capture_rejectsDuplicateLabelAndScreen(tmp_path, monkeypatch):
    assert _run(tmp_path, "--label", "홈", "--route", "/", monkeypatch=monkeypatch)[0] == 0

    assert _run(tmp_path, "--label", "홈", "--route", "/listings", monkeypatch=monkeypatch)[0] == 2
    assert _run(tmp_path, "--label", "또홈", "--route", "/", monkeypatch=monkeypatch)[0] == 2
    assert len(list(tmp_path.glob("*.json"))) == 1


def test_capture_listRoutesHidesPricesInDemo(tmp_path, monkeypatch, capsys):
    code, _ = _run(tmp_path, "--label", "x", "--list-routes", monkeypatch=monkeypatch)

    assert code == 0
    assert capsys.readouterr().out.split() == ["/", "/listings"]
