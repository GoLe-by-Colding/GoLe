import io
import threading
from unittest.mock import Mock

import grpc
import pytest
from PIL import Image

from gole.brick.v1 import brick_images_pb2 as pb
from gole.brick.v1 import brick_images_pb2_grpc as rpc
from gole_brick_filter.grpc_server import create_server
from gole_brick_filter.agent import MAX_INPUT, MAX_OUTPUT
from test_brick_filter import photo

AUTH = (("authorization", "Bearer " + "t" * 32),)


@pytest.fixture
def service():
    editor = Mock()
    editor.edit.return_value = photo()
    server, port = create_server(editor, "t" * 32, 0)
    server.start()
    with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
        yield rpc.BrickImagesStub(channel), editor
    server.stop(0).wait()


def request(mode="MINIFIGURE", source=None):
    return pb.GenerateImageRequest(source_png=photo() if source is None else source, mode=mode)


@pytest.mark.parametrize("mode", ["MINIFIGURE", "BRICK_OBJECT"])
def test_real_grpc_runs_langgraph_without_metadata(service, mode):
    stub, editor = service
    result = stub.Generate(request(mode), timeout=5, metadata=AUTH)
    assert "private" not in Image.open(io.BytesIO(result.result_png)).info
    source, actual_mode = editor.edit.call_args.args
    assert "private" not in Image.open(io.BytesIO(source)).info
    assert actual_mode == mode
    assert editor.edit.call_count == 1


@pytest.mark.parametrize("metadata", [(), (("authorization", "Bearer wrong"),), AUTH + AUTH])
def test_unauthenticated_never_generates(service, metadata):
    stub, editor = service
    with pytest.raises(grpc.RpcError) as failure:
        stub.Generate(request(), timeout=5, metadata=metadata)
    assert failure.value.code() == grpc.StatusCode.UNAUTHENTICATED
    editor.edit.assert_not_called()


@pytest.mark.parametrize("data", [request("CUSTOM"), request(source=b""), request(source=b"not an image")])
def test_invalid_request_never_generates(service, data):
    stub, editor = service
    with pytest.raises(grpc.RpcError):
        stub.Generate(data, timeout=5, metadata=AUTH)
    editor.edit.assert_not_called()


def test_deadline_required_and_bounded(service):
    stub, editor = service
    for timeout in (None, 200):
        with pytest.raises(grpc.RpcError) as failure:
            stub.Generate(request(), timeout=timeout, metadata=AUTH)
        assert failure.value.code() == grpc.StatusCode.INVALID_ARGUMENT
    editor.edit.assert_not_called()


def test_oversized_images_are_rejected(service):
    stub, editor = service
    with pytest.raises(grpc.RpcError) as failure:
        stub.Generate(request(source=b"x" * (MAX_INPUT + 1)), timeout=5, metadata=AUTH)
    assert failure.value.code() == grpc.StatusCode.INVALID_ARGUMENT
    editor.edit.assert_not_called()
    editor.edit.return_value = b"x" * (MAX_OUTPUT + 1)
    with pytest.raises(grpc.RpcError) as failure:
        stub.Generate(request(), timeout=5, metadata=AUTH)
    assert failure.value.code() == grpc.StatusCode.INVALID_ARGUMENT
    assert editor.edit.call_count == 1


def test_provider_error_does_not_leak_or_retry_and_capacity_recovers(service):
    stub, editor = service
    editor.edit.side_effect = RuntimeError("private image and secret token")
    with pytest.raises(grpc.RpcError) as failure:
        stub.Generate(request(), timeout=5, metadata=AUTH)
    assert failure.value.details() == "IMAGE_PROVIDER_UNAVAILABLE"
    assert editor.edit.call_count == 1
    editor.edit.side_effect = None
    assert stub.Generate(request(), timeout=5, metadata=AUTH).result_png


def test_cancel_does_not_release_capacity_while_provider_is_still_running(service):
    stub, editor = service
    entered = threading.Barrier(3)
    release = threading.Event()

    def slow(*_):
        entered.wait(timeout=5)
        assert release.wait(timeout=5)
        return photo()

    editor.edit.side_effect = slow
    calls = [stub.Generate.future(request(), timeout=5, metadata=AUTH) for _ in range(2)]
    try:
        entered.wait(timeout=5)
        for call in calls:
            call.cancel()
        with pytest.raises(grpc.RpcError) as failure:
            stub.Generate(request(), timeout=2, metadata=AUTH)
        assert failure.value.code() == grpc.StatusCode.RESOURCE_EXHAUSTED
        assert editor.edit.call_count == 2
    finally:
        release.set()
