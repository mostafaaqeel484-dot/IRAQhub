#!/usr/bin/env python3
"""Import four owner-approved images into the RX6 source tree only (no APK)."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile

BRANDS={
 "rx6-arena-user.png":[("Arena Channels Group","RS"),("Arena Sport Croatia / HD-WIN","HR"),
                       ("Arena Sport Crna Gora","ME"),("Arena Sport BiH","BA"),("Arena Sport Slovenia","SI")],
 "rx6-digitalb-user.png":[("DigitAlb","AL"),("SuperSport Albania","AL")],
 "rx6-sky-user.png":[("Sky Italia","IT"),("Sky Deutschland","DE"),("Sky Digital","GB")],
 "rx6-canal-user.png":[("Platforma Canal+ Polska","PL")],
}
def apply(pack,root):
 from PIL import Image
 path=root/"app/src/main/assets/provider-branding/real-logo-manifest.json"
 images=path.parent/"images"
 data=json.loads(path.read_text("utf-8"))
 if data.get("channels")!=[]:raise ValueError("CHMAX-only channel art invariant broken")
 uploaded={}
 with ZipFile(pack) as archive:
  for name in BRANDS:
   compressed="min-"+name
   if compressed not in archive.namelist():raise ValueError("Missing "+compressed)
   raw=archive.read(compressed)
   if len(raw)>500000 or len(raw)<1000 or not raw.startswith(b"\x89PNG\r\n\x1a\n"):
    raise ValueError("Not an original PNG: "+compressed)
   img=Image.open(io.BytesIO(raw))
   img.verify()
   img=Image.open(io.BytesIO(raw))
   if not (100<=img.width<=1400 and 70<=img.height<=1400):
    raise ValueError("Invalid dimensions: "+compressed)
   uploaded[name]=raw
 mapped={(p["name"],p["country"]):p for p in data["providers"]}
 for name,raw in uploaded.items():
  images.mkdir(parents=True,exist_ok=True)
  (images/name).write_bytes(raw)
  data["assets"]=[r for r in data["assets"] if r["asset"]!=name]
  data["assets"].append({"asset":name,"local":True,"sha256":hashlib.sha256(raw).hexdigest(),
                         "source":"User-supplied authentic brand reference (RX6) 2026-11-12"})
  for provider,country in BRANDS[name]:
   if (provider,country) in mapped:
    mapped[(provider,country)]["asset"]=name
   else:
    p={"name":provider,"country":country,"asset":name}
    data["providers"].append(p)
    mapped[(provider,country)]=p
 assert len({r["asset"] for r in data["assets"]})==len(data["assets"])
 assert len({(r["name"],r["country"]) for r in data["providers"]})==len(data["providers"])
 assert data["channels"]==[]
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
 print("Imported 4 user logos / 11 provider logo mappings; no channels touched; no APK")
if __name__=="__main__":
 p=argparse.ArgumentParser()
 p.add_argument("--pack",required=True,type=Path)
 p.add_argument("--project-root",default=Path("."),type=Path)
 args=p.parse_args()
 apply(args.pack,args.project_root)
