"""Generate the synthetic fixture; run with Pillow + pillow-heif in an isolated venv."""
from pathlib import Path
from PIL import Image, ImageDraw
import pillow_heif

pillow_heif.register_heif_opener()
image = Image.new("RGB", (80, 48), "red")
ImageDraw.Draw(image).rectangle((40, 0, 79, 47), fill="blue")
exif = Image.Exif()
exif[274] = 6
exif[270] = "GOLE-GPS-FIXTURE-NOT-REAL"
exif[34853] = {1: "N", 2: (37.0, 0.0, 0.0), 3: "E", 4: (127.0, 0.0, 0.0)}
image.info["exif"] = exif.tobytes()
root = Path(__file__).resolve().parents[3]
image.save(root / "apps/api/src/test/resources/media/phone-oriented-gps.heic", format="HEIF", quality=95)
