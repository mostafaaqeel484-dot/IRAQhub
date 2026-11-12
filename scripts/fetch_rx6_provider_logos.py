#!/usr/bin/env python3
"""Build-time bundler for authentic artwork explicitly matched to country/channel.
Pinned source revisions + Git blob SHA ensure stable and non-synthetic logos.

The upstream tv-logo graphics are offered for personal use with attribution,
not as a general redistribution license. Review usage terms before publishing.
This build is an isolated personal-device preview of the user's RX6 launcher.
"""
from __future__ import annotations
import concurrent.futures
import hashlib
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE=Path(__file__).resolve().parents[1]
manifest=BASE/"app/src/main/assets/provider-branding/real-logo-manifest.json"
destination=BASE/"app/src/main/assets/provider-branding/images"
report=BASE/"app/src/main/assets/provider-branding/logo-build-report.json"
meta=json.loads(manifest.read_text("utf-8"))
destination.mkdir(parents=True,exist_ok=True)
versions={
  "tv-logo/tv-logos":meta["sources"]["tvCommit"],
  "simple-icons/simple-icons":meta["sources"]["simpleCommit"]
}
assets=meta["assets"]
assert 10<=len(assets)<=250, "Unexpected provider-only logo count"
assert not meta["channels"], "Channel artwork must not be bundled in RX6 provider-only builds"
assert all(p["asset"] in {a["asset"] for a in assets} for p in meta["providers"])
def fetch_one(row):
    name=row["asset"]
    assert name and "/" not in name and "\\" not in name and name.endswith((".png",".svg"))
    output=destination/name
    if row.get("local",False):
        if not output.is_file():return name,0,"MISSING_USER_ASSET"
        raw=output.read_bytes()
        if not 60<=len(raw)<=500000:return name,0,"INVALID_LOCAL_ASSET_SIZE"
        if hashlib.sha256(raw).hexdigest()!=row.get("sha256",""):
            return name,0,"LOCAL_ART_SHA256_MISMATCH"
        if name.endswith(".svg") and b"<svg" not in raw[:150]:
            return name,0,"LOCAL_ART_NOT_SVG"
        return name,len(raw),"user_supplied_sha256_verified"
    if output.exists():
        return name,output.stat().st_size,"already"
    if "repo" in row:
        origin=row["repo"]
        if origin not in versions:raise ValueError("unapproved origin")
        path=row["path"]
        if path.startswith("/") or ".." in path.split("/"):raise ValueError("invalid path")
        url="https://raw.githubusercontent.com/"+origin+"/"+versions[origin]+"/"+urllib.parse.quote(path,safe="/")
        expected=row["sha"]
    else:
        url=row.get("url","")
        if not (url.startswith("https://commons.wikimedia.org/wiki/Special:Redirect/file/") or
                url.startswith("https://upload.wikimedia.org/wikipedia/commons/") or
                url.startswith("https://thumb.wikimedia.org/wikipedia/commons/thumb/")):
            raise ValueError("unapproved Commons origin")
        expected=None
    max_attempts=1 if row.get("optional",False) else 3
    for attempt in range(max_attempts):
        try:
            req=urllib.request.Request(url,headers={
              "User-Agent":"IRAQhub-RX6-PersonalLogoPreview/1.0 (source attribution in repository)",
              "Accept":"image/png,image/svg+xml,application/octet-stream"})
            with urllib.request.urlopen(req,timeout=10 if row.get("optional",False) else 25) as response:
                final=response.geturl()
                if not (final.startswith("https://raw.githubusercontent.com/") or
                        final.startswith("https://upload.wikimedia.org/") or
                        final.startswith("https://thumb.wikimedia.org/")):
                    raise ValueError("redirect outside trusted image hosts")
                raw=response.read(2*1024*1024+1)
            if len(raw)<60 or len(raw)>2*1024*1024:raise ValueError("bad image byte count")
            if name.endswith(".png") and not raw.startswith(b"\x89PNG\r\n\x1a\n"):raise ValueError("not PNG")
            if name.endswith(".svg") and b"<svg" not in raw[:1000]:raise ValueError("not SVG")
            if expected:
                actual=hashlib.sha1(b"blob "+str(len(raw)).encode("ascii")+b"\x00"+raw).hexdigest()
                if actual!=expected:raise ValueError("pinned Git blob changed")
            output.write_bytes(raw)
            return name,len(raw),"downloaded"
        except (urllib.error.URLError,TimeoutError,OSError) as error:
            status=getattr(error,"code",None)
            if attempt<max_attempts-1:time.sleep(5*(attempt+1) if status in (403,429,503) else 2*(attempt+1))
            else:return name,0,type(error).__name__+(("_"+str(status)) if status else "")
        except Exception as error:
            return name,0,type(error).__name__
    return name,0,"unavailable"

# Wikimedia enforces per-IP request limits on large bursts: never issue
# concurrent Commons logo downloads. GitHub-pinned images can be concurrent.
repo_assets=[entry for entry in assets if "repo" in entry]
commons_assets=[entry for entry in assets if "repo" not in entry]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    repo_results=list(pool.map(fetch_one,repo_assets))
commons_results=[]
for item in commons_assets:
    commons_results.append(fetch_one(item))
    time.sleep(0.6 if item.get("optional",False) else 1.5)
results=repo_results+commons_results
ok=[r for r in results if r[1]>0]
available={r[0] for r in ok}
missing=[{"name":name,"reason":state} for name,n,state in results if not n]
failed_required=[
    row["asset"] for row in assets
    if row["asset"] not in available and not row.get("optional",False)
]
original_provider_count=len(meta["providers"])
if not failed_required and missing:
    # The APK asset manifest must NEVER claim a downloaded logo that is absent.
    # User-visible fallback renders the provider name on a colored card instead.
    meta["providers"]=[p for p in meta["providers"] if p["asset"] in available]
    meta["assets"]=[a for a in meta["assets"] if a["asset"] in available]
    manifest.write_text(json.dumps(meta,ensure_ascii=False,indent=2),"utf-8")
summary={"source":"pinned and verified provider-only artwork",
         "total":len(assets),"downloaded":len(ok),
         "missing":missing[:100],
         "missing_required":failed_required,
         "planned_providers":original_provider_count,
         "providers":len(meta["providers"]),
         "channels":len(meta["channels"]),
         "bytes":sum(x[1] for x in ok)}
report.write_text(json.dumps(summary,indent=2,ensure_ascii=False),encoding="utf-8")
print("RX6 provider artwork:",summary["downloaded"],"/",summary["total"],
      "assets; verified provider bindings:",summary["providers"],
      "of",summary["planned_providers"],"planned; channels:",summary["channels"])
if missing:print("Not packaged (clearly labeled fallback instead):",missing[:15])
if failed_required:
    print("ERROR: required existing verified artwork not available",file=sys.stderr)
    sys.exit(1)
