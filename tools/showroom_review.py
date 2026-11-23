"""Collect actual Android captures into a review sheet alongside the full screenshots."""
import base64
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

root = Path("launch-check")
screens = [("cached", "Home"), ("car-Zoomy", "Garage"), ("tracks", "Courses"), ("race", "Race")]
sheet = Image.new("RGB", (1440, 900), "#172b46")
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 23)
for i, (file, label) in enumerate(screens):
    x, y = (i % 2) * 720, (i // 2) * 450
    with Image.open(root / (file + ".png")) as frame:
        image = ImageOps.contain(frame.convert("RGB"), (704, 396))
        sheet.paste(image, (x + (720-image.width)//2, y + 8 + (396-image.height)//2))
    draw.text((x+15,y+408), label, fill="white", font=font)
dest = root / "Vroom-Buddies-Festival.jpg"
sheet.save(dest, quality=88)
# Stream the review image in short lines so remote reviewers can inspect this exact build.
encoded = base64.b64encode(dest.read_bytes()).decode("ascii")
for i in range(0, len(encoded), 3000):
    print("VROOM_REVIEW", i, encoded[i:i+3000])
