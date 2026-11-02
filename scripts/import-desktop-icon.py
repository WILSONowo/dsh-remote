"""Import an existing desktop mark as Android vector resources (no generated artwork).

Usage: python scripts/import-desktop-icon.py PATH_TO_DESKTOP_ICON_PNG
Requires Pillow, NumPy and opencv-python-headless. The original PNG is copied unchanged.
"""
from pathlib import Path
import hashlib
import shutil
import sys
import cv2
import numpy as np
from PIL import Image

source = Path(sys.argv[1])
root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
pixels = np.asarray(Image.open(source).convert("RGBA"))
mask = ((pixels[:, :, :3].max(axis=2) < 128) & (pixels[:, :, 3] > 127)).astype(np.uint8) * 255
contours, _ = cv2.findContours(mask, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)
paths = []
for contour in contours:
    if cv2.contourArea(contour) < 8:
        continue
    points = cv2.approxPolyDP(contour, 0.65, True).reshape(-1, 2)
    paths.append("M" + " L".join(f"{x},{y}" for x, y in points) + " Z")
path = " ".join(paths)
ys, xs = np.where(mask > 0)
cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
# Keep every silhouette pixel inside the adaptive icon's circular safe zone;
# a width-only bound clips the whale tail under circular launcher masks.
radius = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2).max()
scale = min(64 / (xs.max() - xs.min()), 31 / radius)
tx, ty = 54 - scale * cx, 54 - scale * cy

def vector(name, fill, adaptive):
    viewport = 108 if adaptive else 1024
    content = f'<path android:fillColor="{fill}" android:fillType="evenOdd" android:pathData="{path}" />'
    if adaptive:
        content = f'<group android:scaleX="{scale:.8f}" android:scaleY="{scale:.8f}" android:translateX="{tx:.8f}" android:translateY="{ty:.8f}">{content}</group>'
    (res / "drawable" / name).write_text(f'<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{viewport}dp" android:height="{viewport}dp" android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n{content}\n</vector>\n', encoding="utf-8")

vector("ic_launcher_foreground.xml", "#343942", True)
vector("ic_launcher_monochrome.xml", "#FFFFFFFF", True)
vector("ic_notification_dsh.xml", "#FFFFFFFF", False)
shutil.copyfile(source, res / "drawable-nodpi/ic_dsh_desktop.png")
print("Imported desktop icon SHA-256:", hashlib.sha256(source.read_bytes()).hexdigest())
