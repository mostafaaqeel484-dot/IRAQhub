#!/usr/bin/env python3
"""Import original BEN10 ARMv7 native binaries to GazalAudioSync for authorized builds.

Does not patch licensing, signatures, protected core or OEM firmware.
Run: python3 tools/import_ben10_native.py /path/to/BEN10-original.apk
"""
from __future__ import annotations

import hashlib
import json
import pathlib
import struct
import sys
import zipfile

HERE = pathlib.Path(__file__).resolve().parents[1]
OUT = HERE / "app" / "src" / "main"
NATIVE = ("libben10_fta.so", "libben10_security.so", "libconscrypt_jni.so")
ICONS = ("AnisFM.png", "Best.png", "Fox.png", "Orange.png", "SATFAMILY.png")
OTHER = ("bein_channels.json",)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def check_armv7_elf(data: bytes, name: str):
    if data[:4] != b"\x7fELF" or data[4] != 1 or data[5] != 1:
        raise ValueError(f"{name}: expected little-endian ELF32")
    machine = struct.unpack_from("<H", data, 18)[0]
    if machine != 40:  # EM_ARM
        raise ValueError(f"{name}: expected ARM EABI (machine=40), got {machine}")


def add(source: zipfile.ZipFile, source_name: str, dest: pathlib.Path, verify_elf=False):
    data = source.read(source_name)
    if verify_elf:
        check_armv7_elf(data, source_name)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return {"path": str(dest.relative_to(HERE)), "size": len(data), "sha256": sha256(data)}


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 tools/import_ben10_native.py original_ben10_1.0.7.apk")
    archive = pathlib.Path(sys.argv[1]).expanduser().resolve()
    if not archive.is_file():
        raise SystemExit("Original BEN10 APK not found: " + str(archive))
    imported = []
    with zipfile.ZipFile(archive) as src:
        assert src.testzip() is None, "Corrupt original APK"
        for name in NATIVE:
            imported.append(add(src, "lib/armeabi-v7a/" + name,
                                OUT / "jniLibs" / "armeabi-v7a" / name, True))
        for name in ICONS + OTHER:
            imported.append(add(src, "assets/" + name,
                                OUT / "assets" / "ben10" / name))
    report = {
        "input_original_sha256": sha256(archive.read_bytes()),
        "notes": [
            "JNI library packages are original, unmodified and include normal license checks.",
            "libben10_fta.so imports ben10_authorized from libben10_security.so.",
            "This does not make the source-independent Gazal app functionally equivalent to BEN10.",
            "The protected assets/core.b10 is intentionally not imported.",
            "A valid OEM DVB permission path and appropriate native authorization remain required.",
        ],
        "files": imported,
    }
    out = HERE / "native-import-report.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Added", len(imported), "native libraries/reference assets.")
    print("Report:", out)


if __name__ == "__main__":
    main()
