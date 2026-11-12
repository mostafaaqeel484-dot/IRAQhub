#!/usr/bin/env python3
"""Fail closed if an unreviewed or obviously wrong logo URL is promoted."""
import json
from pathlib import Path
from collections import Counter
base=Path(__file__).resolve().parents[1]
audit=json.loads((base/"app/src/main/assets/provider-branding/logo-corrections-audit.json").read_text("utf-8"))
rows=audit["rows"]
assert len(rows)==234,len(rows)
assert len({r["provider_id"] for r in rows})==234
assert audit["channels_manifest_must_remain_empty"] is True
allowed={"image_candidate_not_yet_downloaded","blocked_wrong_brand_or_flag","needs_source_verification","needs_direct_image_url",
         "keep_existing_or_missing","user_image_available","needs_choice","rejected_by_user","needs_image"}
for r in rows:
 assert r["status"] in allowed,r["provider_id"]
 assert r["automatic_promote"] is False,r["provider_id"]
 if r["status"] in ("blocked_wrong_brand_or_flag","rejected_by_user"):
  assert not r.get("local_asset"),r["provider_id"]
 elif r.get("local_asset"):
  assert r["local_asset"] in ("rx6-arena-user.png","rx6-sky-user.png",
                            "rx6-canal-user.png","rx6-digitalb-user.png")
assert sum(r["status"]=="blocked_wrong_brand_or_flag" for r in rows)>=26
print("Audited",len(rows),"provider decisions:",dict(Counter(r["status"] for r in rows)))
