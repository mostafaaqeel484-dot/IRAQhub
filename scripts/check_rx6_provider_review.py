#!/usr/bin/env python3
"""Strict no-network, no-APK validation of RX6 logo source staging."""
import hashlib
import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/"app/src/main/assets/provider-branding/real-logo-manifest.json").read_text())
review=json.loads((root/"review/RX6_234_provider_artwork_candidates_20261112.json").read_text())
rows=review["providers"]
assert len(rows)==234
assert {r["id"] for r in rows}=={f"TVP{i:04d}" for i in range(1,235)}
assert not manifest["channels"],"Provider manifest must not bundle channel logos."
assert not any(a.get("kind")=="channel" for a in manifest["assets"]),"Channel art in provider manifest"
assets={a["asset"]:a for a in manifest["assets"]}
for p in manifest["providers"]:
    assert p["asset"] in assets,(p,"unresolved asset")
assert not [r for r in rows if r["review_status"]=="wrong_artwork_blocked" and r["candidate_url"]],"Rejected candidate still live"
assert not [r for r in rows if r["review_status"]=="search_page_blocked" and r["candidate_url"]],"Search URL still live"
assert len([r for r in rows if r["review_status"]=="user_artwork_local_linked"])==11
assert next(r for r in rows if r["id"]=="TVP0058")["review_status"]=="user_rejected"
local=[a for a in manifest["assets"] if a.get("local")]
assert len(local)==4
for a in local:
    f=root/"app/src/main/assets/provider-branding/images"/a["asset"]
    assert f.is_file(),f
    assert hashlib.sha256(f.read_bytes()).hexdigest()==a["sha256"],f
print("RX6 logo stage audit OK: 234 review rows; 4 local marks; 11 family aliases; 0 channel logos; no APK built.")
