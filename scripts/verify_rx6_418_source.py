#!/usr/bin/env python3
"""Cheap source-side checks: fail if a CI job checked out an older RX6 branch."""
import pathlib
import re
import xml.etree.ElementTree as ET

root=pathlib.Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text()
assert "versionName '4.0.18-rx6'" in gradle
assert re.search(r'versionCode\s+65\b',gradle)
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
names=[n.get(android+'name','') for n in manifest.iter() if n.tag=='activity']
assert any('LiveFeedActivity' in n for n in names), 'Live Feed entry is absent in manifest'
source_root=root/'app/src/main/java/com/smarthub/plus'
live=(source_root/'LiveFeedActivity.java').read_text()
assert 'https://live-feed.net/' in live
assert 'MIXED_CONTENT_NEVER_ALLOW' in live
assert 'setAllowFileAccess(false)' in live
remote=(source_root/'RemoteDirectionPolicy.java').read_text()
assert 'VOLUME_DOWN' in remote and 'VOLUME_UP' in remote
assert 'LIST_PREVIOUS' in remote and 'LIST_NEXT' in remote
cache=(source_root/'ArtworkCache.java').read_text()
assert 'getFilesDir()' in cache and 'online-artwork' in cache
logos=(source_root/'ChannelLogoCache.kt').read_text()
assert 'resolved-logos' in logos and '2*1024*1024' in logos
assert (root/'app/src/test/java/com/smarthub/plus/RemoteDirectionPolicyTest.java').is_file()
assert (root/'app/src/test/java/com/smarthub/plus/ArtworkCachePrunerTest.java').is_file()
print('PASS: exact RX6 4.0.18 source, manifest, remote, Live Feed, persistent disk art and local tests')
