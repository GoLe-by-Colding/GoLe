"""외부 모델과 이미지 codec 구현. Brain에는 포트로 주입한다."""
from __future__ import annotations

import base64
import io
import warnings
from PIL import Image, ImageOps
from gole_brick_filter.policy import MAX_INPUT, MAX_OUTPUT, MAX_PIXELS, PROMPTS


def sanitize_image(data: bytes, limit: int = MAX_INPUT) -> bytes:
    if not data or len(data) > limit:
        raise ValueError("IMAGE_SIZE")
    with warnings.catch_warnings():
        warnings.simplefilter("error", Image.DecompressionBombWarning)
        with Image.open(io.BytesIO(data)) as image:
            if image.format not in {"PNG", "JPEG"} or getattr(image, "n_frames", 1) != 1:
                raise ValueError("IMAGE_TYPE")
            width, height = image.size
            if width < 1 or height < 1 or width * height > MAX_PIXELS or max(width, height) > 6000:
                raise ValueError("IMAGE_PIXELS")
            image.load()
            clean = ImageOps.exif_transpose(image).convert("RGB")
            # Fresh pixels remove EXIF, ICC and free-text metadata before provider/storage.
            fresh = Image.new("RGB", clean.size)
            fresh.paste(clean)
            output = io.BytesIO()
            fresh.save(output, format="PNG")
    result = output.getvalue()
    if len(result) > limit:
        raise ValueError("IMAGE_SIZE")
    return result


class OpenAIEditor:
    def __init__(self, client, model: str = "gpt-image-2"):
        self.client = client
        self.model = model

    def edit(self, image: bytes, mode: str) -> bytes:
        result = self.client.images.edit(
            model=self.model,
            image=("source.png", image, "image/png"),
            prompt=PROMPTS[mode],
            n=1,
            size="1024x1024",
            quality="low",
            output_format="png",
        )
        if not result.data or len(result.data) != 1 or not result.data[0].b64_json:
            raise ValueError("EMPTY_RESULT")
        encoded = result.data[0].b64_json
        if len(encoded) > ((MAX_OUTPUT + 2) // 3) * 4:
            raise ValueError("RESULT_SIZE")
        return base64.b64decode(encoded, validate=True)
