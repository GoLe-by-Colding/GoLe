"""브랜드 카드 합성. 에이전트가 넘긴 텍스트가 마크업·숫자 주장·임의 파일이 되지 못해야 한다."""

import json

from gole_promotion_agent import compose_cli
from gole_promotion_agent.dryrun import _PIXEL


def _seed_capture(captures, label="목록"):
    captures.mkdir(parents=True, exist_ok=True)
    (captures / "abc.png").write_bytes(_PIXEL)
    (captures / "abc.json").write_text(
        json.dumps({"label": label, "file": "abc.png", "route": "/listings"}), encoding="utf-8"
    )


def _run(tmp_path, *args):
    rendered = []

    def renderer(markup, destination):
        rendered.append(markup)
        destination.write_bytes(_PIXEL)

    code = compose_cli.main(
        [*args, "--captures", str(tmp_path / "captures"), "--out", str(tmp_path / "cards")],
        renderer=renderer,
    )
    return code, rendered


def test_compose_writesCardWithEscapedHeadline(tmp_path):
    _seed_capture(tmp_path / "captures")

    code, rendered = _run(tmp_path, "--capture", "목록", "--headline", "<b>원하는 부품만</b> 골라 보기")

    assert code == 0
    assert "&lt;b&gt;원하는 부품만&lt;/b&gt;" in rendered[0]
    assert "<b>" not in rendered[0]
    assert "data:image/png;base64," in rendered[0]
    meta = json.loads((tmp_path / "cards" / "card-01.json").read_text(encoding="utf-8"))
    assert meta == {"file": "card-01.png", "capture": "목록", "headline": "<b>원하는 부품만</b> 골라 보기"}


def test_compose_rejectsDigitsAndLongHeadline(tmp_path):
    _seed_capture(tmp_path / "captures")

    assert _run(tmp_path, "--capture", "목록", "--headline", "거래 3,000건 돌파")[0] == 2
    assert _run(tmp_path, "--capture", "목록", "--headline", "전각 ３개")[0] == 2
    assert _run(tmp_path, "--capture", "목록", "--headline", "가" * 41)[0] == 2
    assert not (tmp_path / "cards").exists()


def test_compose_onlyAcceptsCapturedLabels(tmp_path):
    _seed_capture(tmp_path / "captures")

    code, rendered = _run(tmp_path, "--capture", "../../etc/passwd", "--headline", "보기")

    assert code == 2
    assert rendered == []
