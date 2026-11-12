#!/usr/bin/env python3
"""Offline prebuild QA for provider logos. Does not build APK or use network."""
from __future__ import annotations
import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CSV = ROOT / "artwork-review/RX6_234_Logo_Audit_Report.csv"
BASE = ROOT / "app/src/main/assets/provider-branding"
EXPECTED = {
    "commons-rx6-owner-arena.svg": (
        "a866b0136c2e9f7dbda1d2a14e956e094ab17af49c6698a88209cc0796265206",
        {("Arena Channels Group","RS"),("Arena Sport Croatia / HD-WIN","HR"),
         ("Arena Sport Crna Gora","ME"),("Arena Sport BiH","BA"),
         ("Arena Sport Slovenia","SI")}),
    "commons-rx6-owner-digitalb.svg": (
        "433f6322e05ed268ebff93daa6036bc2c9407c1e0ddf7147a76b90ea10a6d9ac",
        {("DigitAlb","AL"),("SuperSport Albania","AL")}),
    "commons-rx6-owner-sky.svg": (
        "29bc682eb3afb82e117773757d9e7038c3215aa5222c5b9a13ac7804d27b66f6",
        {("Sky Italia","IT"),("Sky Deutschland","DE"),("Sky Digital","GB")}),
    "commons-rx6-owner-canal.svg": (
        "86d3c05199355e337dd24df297fe2c2928b0fdfc2820a1d0b657da3f533fe0b7",
        {("Platforma Canal+ Polska","PL")}),
}

def main():
    with CSV.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    assert len(rows)==234, f"234 provider rows required; received {len(rows)}"
    assert {r["provider_id"] for r in rows} == {f"TVP{i:04d}" for i in range(1,235)}
    blocked=[r for r in rows if r["image_review"]=="blocked_wrong_brand_or_flag"]
    assert len(blocked)>=26, f"Known bad artwork must be rejected: {len(blocked)}"
    assert all(r["use_submitted_url"].lower()=="false" for r in blocked)
    assert sum(r["review_action"]=="reject" for r in rows)==1

    doc=json.loads((BASE/"real-logo-manifest.json").read_text("utf-8"))
    assert doc["channels"]==[], "CHMAX channel logos must never join provider manifest"
    assets={a["asset"]:a for a in doc["assets"]}
    pairs={(p["name"],p["country"]):p["asset"] for p in doc["providers"]}
    assert len(assets)==len(doc["assets"]) and len(pairs)==len(doc["providers"])
    assert all(asset in assets for asset in pairs.values())
    for filename,(sha,owners) in EXPECTED.items():
        item=assets.get(filename)
        assert item and item.get("local") is True
        raw=(BASE/"images"/filename).read_bytes()
        assert b"<svg" in raw[:500] and b"<!DOCTYPE" not in raw and b"<!ENTITY" not in raw
        assert hashlib.sha256(raw).hexdigest()==sha==item["sha256"], f"Altered local logo: {filename}"
        for key in owners:
            assert pairs.get(key)==filename, f"Logo family association wrong: {key}"
    print("RX6 source logo QA PASS: 234 decisions, 26 blocked bad links,")
    print(f"  4 owner-approved brand art files, 11 family mappings, {len(pairs)} provider entries, 0 channel logos")
    print("No network, no Gradle, no APK build.")

if __name__=="__main__":
    main()
