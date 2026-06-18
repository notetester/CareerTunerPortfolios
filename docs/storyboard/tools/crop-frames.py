# qa-full.png(전체 페이지, zoom 반영)에서 frames.json 좌표대로 프레임별로 잘라 저장.
import json
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "output"
meta = json.loads((OUT / "frames.json").read_text(encoding="utf-8"))
dsf = meta["dsf"]
img = Image.open(OUT / "qa-full.png")
W = img.width
for i, r in enumerate(meta["rects"], 1):
    top = r["top"] * dsf
    h = r["height"] * dsf
    box = (0, max(0, top - 6 * dsf), W, min(img.height, top + h + 6 * dsf))
    img.crop(box).save(OUT / f"qa-static-{i:02d}.png")
    print(f"f{i}: {box[2]}x{box[3]-box[1]}")
print("done")
