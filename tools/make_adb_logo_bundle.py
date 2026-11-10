#!/usr/bin/env python3
"""Package accessible, authorised receiver logos with exact IRAQhub DVB IDs.

Usage: python3 tools/make_adb_logo_bundle.py channels.csv logos/ adb-logos.zip
CSV headings: physical,file
  DVB:130:Ku:11096:H:27500:101,channel101.png

USB ADB copies from a user's authorised device; this script does NOT read
other apps' private directories, elevate ADB privileges, or guess names.
"""
import csv
import hashlib
import json
import pathlib
import re
import sys
import zipfile

def run(csv_name: str, image_root: str, out_name: str) -> int:
    root = pathlib.Path(image_root).resolve(strict=True)
    if not root.is_dir():
        raise ValueError("images directory required")
    channels = []
    seen = set()
    with open(csv_name, encoding="utf-8-sig", newline="") as stream:
        for item in csv.DictReader(stream):
            physical = item.get("physical", "").strip()
            filename = item.get("file", "").strip()
            if not re.fullmatch(r"DVB:[A-Za-z0-9:-]{8,120}", physical):
                raise ValueError("invalid exact DVB physical identity")
            if not re.fullmatch(r"[A-Za-z0-9_.-]+\.(?:png|jpg|jpeg)", filename, re.I):
                raise ValueError("unsafe image filename")
            if physical in seen or len(channels) >= 400:
                raise ValueError("duplicate physical or more than 400")
            seen.add(physical)
            image = (root / filename).resolve(strict=True)
            if root not in image.parents or not image.is_file():
                raise ValueError("image outside selected directory")
            if not 16 <= image.stat().st_size <= 1024 * 1024:
                raise ValueError("image outside 16 B - 1 MiB")
            channels.append((physical, filename, image, hashlib.sha256(image.read_bytes()).hexdigest()))
    if not channels:
        raise ValueError("no verified channels")
    dest = pathlib.Path(out_name)
    with zipfile.ZipFile(dest, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        for _, filename, image, _ in channels:
            bundle.write(image, "logos/" + filename)
        bundle.writestr("manifest.json", json.dumps({
            "version": 1,
            "channels": [
                {"physical": key, "file": "logos/" + file, "sha256": digest}
                for key, file, _, digest in channels
            ]
        }, ensure_ascii=False, separators=(",", ":")))
    print(f"Packaged {len(channels)} verified DVB logos: {dest}")
    return 0

if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit("Usage: make_adb_logo_bundle.py channels.csv logo-directory output.zip")
    try:
        sys.exit(run(*sys.argv[1:]))
    except (OSError, ValueError) as error:
        sys.exit(str(error))
