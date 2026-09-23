"""Isolated HEVC still-image decoder; invoked with fixed arguments, never a shell."""
import os
import resource
import sys
import warnings


def main():
    # Apply OS limits before loading native codecs or touching untrusted input.
    resource.setrlimit(resource.RLIMIT_AS, (1024 * 1024 * 1024,) * 2)
    resource.setrlimit(resource.RLIMIT_CPU, (8, 8))
    resource.setrlimit(resource.RLIMIT_FSIZE, (32 * 1024 * 1024,) * 2)
    resource.setrlimit(resource.RLIMIT_NOFILE, (32, 32))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    from PIL import Image
    import pillow_heif

    if tuple(map(int, pillow_heif.libheif_version().split(".")[:3])) < (1, 23, 4):
        sys.exit(69)
    pillow_heif.register_heif_opener(thumbnails=False)
    source, output = sys.argv[1:3]
    width, height, pixels, max_bytes = map(int, sys.argv[3:7])
    Image.MAX_IMAGE_PIXELS = pixels
    warnings.simplefilter("error", Image.DecompressionBombWarning)
    if os.path.getsize(source) > max_bytes:
        sys.exit(65)
    with Image.open(source, formats=["HEIF"]) as image:
        if image.format != "HEIF" or getattr(image, "n_frames", 1) != 1:
            sys.exit(65)
        w, h = image.size
        if min(w, h) <= 0 or w > width or h > height or w * h > pixels:
            sys.exit(65)
        # libheif applies irot/imir transformations; the Pillow plugin resets EXIF orientation.
        # Do not apply the same orientation a second time.
        image.load()
        w, h = image.size
        if min(w, h) <= 0 or w > width or h > height or w * h > pixels:
            sys.exit(65)
        rgb = image.convert("RGB")
        clean = Image.new("RGB", rgb.size)
        clean.paste(rgb)
        clean.save(output, format="JPEG", quality=90, exif=b"", icc_profile=None)
    if os.path.getsize(output) > max_bytes:
        sys.exit(65)


if __name__ == "__main__":
    try:
        main()
    except (ImportError, AttributeError):
        sys.exit(69)  # Decoder absent/incompatible: no original bytes fallback.
    except Exception:
        sys.exit(65)  # Never echo native errors, local paths, metadata or image contents.
