#!/usr/bin/env python3
"""Cheap source-side checks: fail if a CI job checked out an older RX6 branch."""
import pathlib
import re
import xml.etree.ElementTree as ET

root=pathlib.Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text()
assert "versionName '4.0.20-rx6-turbo-feeds'" in gradle
assert re.search(r'versionCode\s+67\b',gradle)
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
names=[n.get(android+'name','') for n in manifest.iter() if n.tag=='activity']
assert any('LiveFeedActivity' in n for n in names), 'Live Feed entry is absent in manifest'
source_root=root/'app/src/main/java/com/smarthub/plus'
live=(source_root/'LiveFeedActivity.java').read_text()
native=(source_root/'LiveFeedNativeActivity.java').read_text()
parser=(source_root/'LiveFeedTextParser.java').read_text()
assert 'extends LiveFeedNativeActivity' in live
assert 'WebView' not in native and 'new WebView' not in live
assert 'https://live-feed.net/' in native
assert 'LiveFeedTextParser.parse' in native
assert 'No BISS keys' in native
assert 'Pattern RF=' in parser and 'Pattern ORBIT=' in parser
assert (root/'app/src/test/java/com/smarthub/plus/LiveFeedTextParserTest.java').is_file()
policy=(source_root/'SavedLogoSourcePolicy.java').read_text()
logos=(source_root/'LogoRepository.kt').read_text()
assert 'SavedLogoSourcePolicy.allow' in logos
assert 'CHMAX_AUTHORISED' in policy
assert (root/'app/src/test/java/com/smarthub/plus/SavedLogoSourcePolicyTest.java').is_file()
player=(source_root/'DvbPlaybackActivity.java').read_text()
local_info=(source_root/'LocalInfoBarController.java').read_text()
library=(source_root/'ReceiverLibraryActivity.java').read_text()
assert 'localInfo.showInfoBar()' in player
assert 'void showInfoBar()' in local_info
assert 'openPlayingInfoBar()' in library
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
assert "applicationIdSuffix '.rx6api420'" in gradle
print('PASS: RX6 4.0.20 Turbo fixes, native feeds, CHMAX disk logos, INFO, and 4.18 baseline')

