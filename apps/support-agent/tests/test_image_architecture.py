import ast
from concurrent.futures import ThreadPoolExecutor
from dataclasses import fields
from pathlib import Path
from unittest.mock import Mock

import pytest

from gole_brick_filter import brain, hands
from gole_brick_filter.runtime import ImageHarness, InactiveRequest, InvalidImage, ProviderUnavailable
from gole_brick_filter.session import Audit, EphemeralSession, Stage
from test_brick_filter import photo


def imported_modules(module):
    tree = ast.parse(Path(module.__file__).read_text())
    return {node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)} | {
        alias.name for node in ast.walk(tree) if isinstance(node, ast.Import) for alias in node.names
    }


def test_brain_only_depends_on_graph_policy_and_ports():
    assert imported_modules(brain) <= {
        "__future__", "typing", "langgraph.graph", "gole_brick_filter.policy", "gole_brick_filter.ports"
    }
    assert not any(name.startswith(("langgraph", "grpc", "sqlite3")) for name in imported_modules(hands))


def test_session_is_bounded_metadata_only_and_terminal():
    session = EphemeralSession()
    for stage in [Stage.VALIDATING, Stage.GENERATING, Stage.REVIEWING, Stage.SUCCEEDED]:
        session.advance(stage)
    assert [field.name for field in fields(Audit)] == ["status", "events"]
    assert session.snapshot().events == (
        Stage.ACCEPTED, Stage.VALIDATING, Stage.GENERATING, Stage.REVIEWING, Stage.SUCCEEDED
    )
    with pytest.raises(ValueError, match="SESSION_TERMINAL"):
        session.advance(Stage.GENERATING)


def test_session_rejects_arbitrary_event_payload_and_skipped_validation():
    session = EphemeralSession()
    for event in ["private provider body", Stage.SUCCEEDED, Stage.REVIEWING]:
        with pytest.raises(ValueError):
            session.advance(event)
    assert session.snapshot().events == (Stage.ACCEPTED,)


def test_harness_returns_result_with_no_image_in_audit_or_repr():
    editor = Mock()
    editor.edit.return_value = photo()
    result = ImageHarness(editor).run("MINIFIGURE", photo())
    assert result.image
    assert result.audit.status == Stage.SUCCEEDED
    assert "PNG" not in repr(result)
    assert editor.edit.call_count == 1


def test_harness_does_not_call_hands_after_cancellation():
    editor = Mock()

    def cancelled():
        raise InactiveRequest()

    with pytest.raises(InactiveRequest):
        ImageHarness(editor).run("MINIFIGURE", photo(), cancelled)
    editor.edit.assert_not_called()


def test_harness_sanitizes_failure_and_reuses_capacity_without_retry():
    editor = Mock()
    editor.edit.side_effect = RuntimeError("private-user-image-and-key")
    harness = ImageHarness(editor)
    with pytest.raises(ProviderUnavailable) as failure:
        harness.run("MINIFIGURE", photo())
    assert str(failure.value) == ""
    assert editor.edit.call_count == 1
    editor.edit.side_effect = None
    editor.edit.return_value = photo()
    assert harness.run("BRICK_OBJECT", photo()).audit.status == Stage.SUCCEEDED


def test_harness_isolates_concurrent_sessions():
    editor = Mock()
    editor.edit.return_value = photo()
    harness = ImageHarness(editor)
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda mode: harness.run(mode, photo()), ["MINIFIGURE", "BRICK_OBJECT"]))
    assert all(result.audit.status == Stage.SUCCEEDED for result in results)
    assert results[0].audit is not results[1].audit
    assert len(results[0].audit.events) == len(results[1].audit.events) == 5


def test_invalid_input_never_calls_hands():
    editor = Mock()
    with pytest.raises(InvalidImage):
        ImageHarness(editor).run("CUSTOM", photo())
    editor.edit.assert_not_called()


def test_legacy_http_transport_uses_harness():
    import threading
    from http.server import ThreadingHTTPServer
    import httpx
    from gole_brick_filter.server import make_handler

    editor = Mock()
    editor.edit.return_value = photo()
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(ImageHarness(editor), "t" * 32))
    thread = threading.Thread(target=server.serve_forever)
    thread.start()
    try:
        headers = {"Authorization": "Bearer " + "t" * 32, "Content-Type": "image/png", "X-Brick-Mode": "CUSTOM"}
        url = f"http://127.0.0.1:{server.server_port}/internal/brick-filter"
        with httpx.Client() as client:
            assert client.post(url, headers=headers, content=photo()).status_code == 422
            editor.edit.assert_not_called()
            headers["X-Brick-Mode"] = "BRICK_OBJECT"
            assert client.post(url, headers=headers, content=photo()).status_code == 200
        assert editor.edit.call_count == 1
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
