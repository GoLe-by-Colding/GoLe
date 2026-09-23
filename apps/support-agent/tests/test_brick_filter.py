import base64
import io
from types import SimpleNamespace
from unittest.mock import Mock

import httpx
import pytest
from openai import OpenAI
from PIL import Image, PngImagePlugin
from gole_brick_filter.agent import OpenAIEditor, build_graph, sanitize_image, MAX_INPUT


def photo():
    output = io.BytesIO()
    info = PngImagePlugin.PngInfo()
    info.add_text("private", "original metadata")
    Image.new("RGB", (16, 16), "blue").save(output, "PNG", pnginfo=info)
    return output.getvalue()


@pytest.mark.parametrize("mode", ["MINIFIGURE", "BRICK_OBJECT"])
def test_graph_validates_generates_and_clears_source(mode):
    editor = Mock()
    editor.edit.return_value = photo()
    result = build_graph(editor).invoke({"source": photo(), "mode": mode})
    assert result["source"] == b""
    assert "private" not in Image.open(io.BytesIO(result["result"])).info
    assert editor.edit.call_args.args[1] == mode


@pytest.mark.parametrize("data", [b"", b"<svg></svg>", b"x" * (MAX_INPUT + 1)],
                         ids=["empty", "svg", "oversized"])
def test_invalid_image_never_calls_provider(data):
    editor = Mock()
    with pytest.raises(Exception):
        build_graph(editor).invoke({"source": data, "mode": "MINIFIGURE"})
    editor.edit.assert_not_called()


def test_invalid_mode_and_large_pixels():
    editor = Mock()
    with pytest.raises(ValueError):
        build_graph(editor).invoke({"source": photo(), "mode": "CUSTOM_PROMPT"})
    out = io.BytesIO()
    Image.new("RGB", (6001, 1)).save(out, "PNG")
    with pytest.raises(ValueError):
        sanitize_image(out.getvalue())
    editor.edit.assert_not_called()


def test_installed_sdk_images_edit_multipart_contract_no_network():
    seen = []
    def handler(request):
        seen.append(request)
        body = request.read()
        assert request.url.path == "/v1/images/edits"
        assert b'filename="source.png"' in body
        for value in [b"gpt-image-2", b"1024x1024", b"image/png", b"MINIFIGURE_NOT_RAW_PROMPT"]:
            if value == b"MINIFIGURE_NOT_RAW_PROMPT":
                assert value not in body
            else:
                assert value in body
        return httpx.Response(200, json={"created": 1, "data": [{"b64_json": base64.b64encode(photo()).decode()}]})
    client = OpenAI(api_key="test-not-a-real-key", max_retries=0, http_client=httpx.Client(transport=httpx.MockTransport(handler)))
    assert OpenAIEditor(client).edit(photo(), "MINIFIGURE") == photo()
    assert len(seen) == 1


def test_provider_failure_not_retried():
    seen = []
    def handler(request):
        seen.append(request)
        return httpx.Response(503, json={"error": {"message": "unavailable"}})
    client = OpenAI(api_key="test-only", max_retries=0, http_client=httpx.Client(transport=httpx.MockTransport(handler)))
    with pytest.raises(Exception):
        build_graph(OpenAIEditor(client)).invoke({"source": photo(), "mode": "BRICK_OBJECT"})
    assert len(seen) == 1


def test_no_empty_or_url_only_provider_output():
    client = Mock()
    client.images.edit.return_value = SimpleNamespace(data=[SimpleNamespace(b64_json=None)])
    with pytest.raises(ValueError):
        OpenAIEditor(client).edit(photo(), "MINIFIGURE")


def test_private_internal_http_rejects_untrusted_requests_and_returns_image():
    import threading
    from http.server import ThreadingHTTPServer
    from gole_brick_filter.server import make_handler
    graph = Mock()
    graph.invoke.return_value = {"result": photo()}
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(graph, "t" * 32))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        url = f"http://127.0.0.1:{server.server_port}/internal/brick-filter"
        with httpx.Client() as client:
            assert client.post(url, content=photo()).status_code == 401
            graph.invoke.assert_not_called()
            headers = {"Authorization": f"Bearer {'t' * 32}", "Content-Type": "text/plain"}
            assert client.post(url, headers=headers, content=photo()).status_code == 400
            graph.invoke.assert_not_called()
            headers.update({"Content-Type": "image/png", "X-Brick-Mode": "MINIFIGURE"})
            response = client.post(url, headers=headers, content=photo())
            assert response.status_code == 200
            assert response.headers["cache-control"] == "no-store"
            assert response.content == photo()
            assert graph.invoke.call_args.args[0]["mode"] == "MINIFIGURE"
    finally:
        server.shutdown()
        server.server_close()
        thread.join()
