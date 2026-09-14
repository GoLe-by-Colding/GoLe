"""Brain이 사용하는 실행 계약. SDK·환경변수·전송 계층에 의존하지 않는다."""
from typing import Protocol


class Editor(Protocol):
    def edit(self, image: bytes, mode: str) -> bytes: ...


class ImageSanitizer(Protocol):
    def __call__(self, data: bytes, limit: int) -> bytes: ...
