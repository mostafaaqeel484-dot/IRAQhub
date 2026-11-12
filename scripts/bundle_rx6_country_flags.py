#!/usr/bin/env python3
"""Build RX6 offline image flags (PNG), independent of manufacturer CHMAX channel art.

Reads the approved provider/country register. Flags are downloaded only during
CI/build; no emoji, remote requests or invented countries at InfoBar runtime.
Source: https://github.com/hampusborgos/country-flags (public-domain flags).
Alternate: https://flagcdn.com (independently maintained flag PNGs).
"""
import concurrent.futures
import gzip
import json
from pathlib import Path
import struct
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/provider-branding/catalog-v1.json.gz"
OUTPUT = ROOT / "app/src/main/assets/provider-branding/country-flags"
REQUIRED = {"AE", "AL", "RS", "HR", "PL", "FR", "GB", "IQ", "TR", "QA"}
HEADERS = {"User-Agent": "IRAQhub-RX6-Flag-Bundler/1.0",
           "Accept": "image/png"}
PNG = b"\x89PNG\r\n\x1a\n"


def valid_png(data):
    if len(data) < 70 or len(data) > 150000 or not data.startswith(PNG):
        return False
    if data[12:16] != b"IHDR":
        return False
    width, height = struct.unpack(">II", data[16:24])
    return 24 <= width <= 1000 and 12 <= height <= 1000


def fetch(country):
    key = country.lower()
    target = OUTPUT / (key + ".png")
    if target.is_file() and valid_png(target.read_bytes()):
        return country, "cached"
    urls = [
        f"https://raw.githubusercontent.com/hampusborgos/country-flags/main/png100px/{key}.png",
        f"https://raw.githubusercontent.com/hampusborgos/country-flags/master/png100px/{key}.png",
        f"https://flagcdn.com/w80/{key}.png",
    ]
    for url in urls:
        for attempt in range(2):
            try:
                request = urllib.request.Request(url, headers=HEADERS)
                with urllib.request.urlopen(request, timeout=12) as stream:
                    if int(stream.headers.get("Content-Length", "0") or 0) > 150000:
                        break
                    data = stream.read(150001)
                if not valid_png(data):
                    break
                target.write_bytes(data)
                return country, url
            except Exception:
                if attempt == 0:
                    time.sleep(.3)
    return country, None


def main():
    with gzip.open(CATALOG, "rt", encoding="utf-8") as stream:
        catalog = json.load(stream)
    countries = {p[1].upper() for p in catalog["p"] if len(p) >= 2
                 and isinstance(p[1], str) and len(p[1]) == 2 and p[1].isalpha()}
    countries.update(REQUIRED)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        results = dict(executor.map(fetch, sorted(countries)))
    missing = sorted(c for c, source in results.items() if source is None)
    installed = sorted(c for c, source in results.items() if source is not None)
    manifest = {
        "version": 1,
        "licence_note": "Country flags are public-domain or jurisdiction-specific national symbols; see respective source",
        "source": "hampusborgos/country-flags; flagcdn.com fallback",
        "countries": installed,
        "missing": missing,
    }
    (OUTPUT / "index.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"RX6 real country flags: {len(installed)}/{len(countries)} available, missing={missing}")
    unavailable = REQUIRED.intersection(missing)
    if unavailable:
        raise SystemExit(f"Missing required real flag image(s): {sorted(unavailable)}")
    if len(installed) < max(20, len(countries) * 4 // 5):
        raise SystemExit("Too many missing flag images; refusing emoji fallback build")


if __name__ == "__main__":
    main()
