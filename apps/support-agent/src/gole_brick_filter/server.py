from __future__ import annotations

import hmac
import os
from gole_agent_runtime.privacy import reject_external_tracing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import BoundedSemaphore

from gole_brick_filter.policy import MAX_INPUT
from gole_brick_filter.hands import OpenAIEditor
from gole_brick_filter.runtime import ImageHarness, InvalidImage, CapacityExceeded


def make_handler(graph, token: str):
    if len(token) < 32:
        raise ValueError("BRICK_FILTER_INTERNAL_TOKEN must contain at least 32 characters")
    capacity = BoundedSemaphore(2)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass  # No request URLs, headers, input or provider error bodies in logs.

        def do_POST(self):  # noqa: N802
            self.connection.settimeout(10)
            if self.path != "/internal/brick-filter":
                self.send_error(404)
                return
            if not hmac.compare_digest(self.headers.get("Authorization", ""), f"Bearer {token}"):
                self.send_error(401)
                return
            if self.headers.get("Transfer-Encoding") or self.headers.get("Content-Type") != "image/png":
                self.send_error(400)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = 0
            if not 0 < length <= MAX_INPUT:
                self.send_error(413)
                return
            if not capacity.acquire(blocking=False):
                self.send_error(429)
                return
            try:
                data = self.rfile.read(length)
                if len(data) != length:
                    self.send_error(400)
                    return
                result = graph.invoke({"mode": self.headers.get("X-Brick-Mode", ""), "source": data})["result"]
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(result)))
                self.end_headers()
                self.wfile.write(result)
            except CapacityExceeded:
                self.send_error(429)
            except (ValueError, InvalidImage):
                self.send_error(422, "Image processing failed")
            except Exception:
                self.send_error(503, "Image processing unavailable")
            finally:
                capacity.release()
    return Handler


def serve():
    # A key alone never enables billable calls. Support-agent entrypoint is unchanged.
    if os.environ.get("BRICK_FILTER_PROVIDER_ENABLED") != "true":
        raise RuntimeError("Brick filter provider is disabled")
    reject_external_tracing()
    from openai import OpenAI
    client = OpenAI(timeout=120.0, max_retries=0)
    graph = ImageHarness(OpenAIEditor(client, os.environ.get("BRICK_FILTER_MODEL", "gpt-image-2")))
    server = ThreadingHTTPServer(
        (os.environ.get("BRICK_FILTER_BIND", "127.0.0.1"), int(os.environ.get("BRICK_FILTER_PORT", "50052"))),
        make_handler(graph, os.environ.get("BRICK_FILTER_INTERNAL_TOKEN", "")),
    )
    server.serve_forever()


if __name__ == "__main__":
    serve()
