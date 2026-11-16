#!/usr/bin/env python3
"""Cheap source-side checks: fail if a CI job checked out an older RX6 branch."""
import pathlib
import re
import xml.etree.ElementTree as ET

root=pathlib.Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text()
assert "versionName '4.0.19-rx6-turbo'" in gradle
assert re.search(r'versionCode\s+66\b',gradle)
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

turbo_pruner=(source_root/'ArtworkCachePruner.java').read_text()
assert 'markRead(File file)' in turbo_pruner and 'ArtworkCachePruner::accessed' in turbo_pruner
assert 'RECENT=Collections.synchronizedMap' in turbo_pruner
assert 'ArtworkCachePruner.markRead(file)' in cache
media=(source_root/'MediaRepository.java').read_text()
assert 'readLocalPoster(file)' in media and 'inJustDecodeBounds=true' in media
menu=(source_root/'ReceiverLibraryActivity.java').read_text()
assert 'setTextIfChanged(TextView view' in menu
test=(root/'app/src/test/java/com/smarthub/plus/ArtworkCachePrunerTest.java').read_text()
assert 'recentlyReadPosterSurvivesEvenIfDownloadedEarlier' in test
assert "applicationIdSuffix '.rx6api419'" in gradle
print('PASS: RX6 4.0.19 Turbo exact source, 4.18 baseline, bounded flash LRU and sampled images')

