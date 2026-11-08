#!/usr/bin/env python3
"""Install the user-provided IRAQhub logo into Android resources.

Usage: python3 tools/install_iraqhub_logo.py /path/to/original-logo.jpg
Requires Pillow. Does not redistribute the source artwork.
"""
import io
import sys
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SIZES = {"mipmap-mdpi":48,"mipmap-hdpi":72,"mipmap-xhdpi":96,
         "mipmap-xxhdpi":144,"mipmap-xxxhdpi":192}

def save_png(image, resource_dir, filename):
    folder = ROOT / "app" / "src" / "main" / "res" / resource_dir
    folder.mkdir(parents=True, exist_ok=True)
    image.save(folder / filename, format="PNG")

def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 tools/install_iraqhub_logo.py /path/to/logo.jpg")
    original = Image.open(sys.argv[1]).convert("RGB")
    for density, size in SIZES.items():
        save_png(original.resize((size,size), Image.Resampling.LANCZOS),
                 density, "iraqhub_logo.png")
    save_png(original.resize((1024,1024), Image.Resampling.LANCZOS),
             "drawable-nodpi", "iraqhub_logo_full.png")
    banner = ImageOps.contain(original, (320,180), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB",(320,180),"black")
    canvas.paste(banner, ((320-banner.width)//2,(180-banner.height)//2))
    save_png(canvas, "drawable-nodpi", "iraqhub_tv_banner.png")
    print("Logo assets generated. Switch manifest icon/banner references after checking files.")

if __name__ == "__main__":
    main()
