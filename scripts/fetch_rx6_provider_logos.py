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
assert 350<=len(assets)<=2000, "Unexpected real logo manifest size"
def fetch_one(row):
    name=row["asset"]
    assert name and "/" not in name and "\\" not in name and name.endswith((".png",".svg"))
    output=destination/name
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
                url.startswith("https://upload.wikimedia.org/wikipedia/commons/")):
            raise ValueError("unapproved Commons origin")
        expected=None
    for attempt in range(3):
        try:
            req=urllib.request.Request(url,headers={
              "User-Agent":"IRAQhub-RX6-PersonalLogoPreview/1.0 (source attribution in repository)",
              "Accept":"image/png,image/svg+xml,application/octet-stream"})
            with urllib.request.urlopen(req,timeout=25) as response:
                final=response.geturl()
                if not (final.startswith("https://raw.githubusercontent.com/") or
                        final.startswith("https://upload.wikimedia.org/")):
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
            if attempt<2:time.sleep(2*(attempt+1))
            else:return name,0,type(error).__name__
        except Exception as error:
            return name,0,type(error).__name__
    return name,0,"unavailable"

with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
    results=list(pool.map(fetch_one,assets))
ok=[r for r in results if r[1]>0]
missing=[{"name":name,"reason":state} for name,n,state in results if not n]
summary={"source":"pinned verified manifest",
         "total":len(assets),"downloaded":len(ok),
         "missing":missing[:100],
         "channels":len(meta["channels"]),
         "providers":len(meta["providers"]),
         "bytes":sum(x[1] for x in ok)}
report.write_text(json.dumps(summary,indent=2,ensure_ascii=False),encoding="utf-8")
print("RX6 real-logo bundler:",summary["downloaded"],"/",summary["total"],
      "images; providers:",summary["providers"],"channel matches:",summary["channels"],
      "total MB:",round(summary["bytes"]/1048576,1))
if missing:print("Missing sample:",missing[:15])
# Never silently ship a pseudo-logo-only build if downloads are blocked.
if len(ok)<300 or missing:
    print("ERROR: not all verified logo assets were bundled",file=sys.stderr)
    sys.exit(1)
