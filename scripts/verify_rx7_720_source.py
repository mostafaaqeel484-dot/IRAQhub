#!/usr/bin/env python3
"""Cheap source-side checks: fail if a CI job checked out an older RX6 branch."""
import pathlib
import re
import xml.etree.ElementTree as ET

root=pathlib.Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text()
assert "versionName '7.2.0-rx7'" in gradle
assert re.search(r'versionCode\s+72\b',gradle)
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
blue_routing=(source_root/'BlueInfoKeyRouting.java').read_text()
access=(source_root/'SatelliteAccessibilityService.java').read_text()
assert (root/'app/src/test/java/com/smarthub/plus/BlueInfoKeyRoutingTest.java').is_file()
assert 'BlueInfoKeyRouting.handles(key)' in player and 'BlueInfoKeyRouting.handles(k)' in library
assert 'BlueKeyGesture blue=' not in player and 'blue.down()' not in player
assert 'localInfo.showProgrammeDetails()' not in player
assert 'startActivity(new Intent(this,SignalInformationActivity.class))' not in library
assert 'SatelliteCompanionService.ACTION_INFO' in access
assert 'SatelliteCompanionService.ACTION_OPEN' not in access
assert 'shouldShow' in blue_routing

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
assert "applicationIdSuffix '.rx7ep3720'" in gradle
preview=(source_root/'Rx7HomeVideoPreview.java').read_text()
policy=(source_root/'Rx7PreviewPolicy.java').read_text()
settings=(source_root/'HubSettingsActivity.java').read_text()
home=(source_root/'LauncherActivity.java').read_text()
preview_settings=(source_root/'Rx7HomePreviewSettingsActivity.java').read_text()
assert any('Rx7HomePreviewSettingsActivity' in n for n in names)
assert "case IMPORT: showImport()" in settings and "case API: showApi()" in settings
assert "ReceiverDbImportActivity.class" in settings and "TsServiceImportActivity.class" in settings
assert "PosterApiSettingsActivity.class" in settings and "ProviderSetupActivity.class" in settings
assert "new Rx7HomeVideoPreview" in home
assert "programmePoster.setVisibility" in home
assert "Gravity.TOP|Gravity.LEFT" in home
assert "EpgArtwork.loadInfobarCached" in home
assert "channelLogo.setImageBitmap" in home
assert "EpgArtwork.loadInfobarBroadcast" in home
assert "setVolume(0f,0f)" in preview and "MEDIA_INFO_VIDEO_RENDERING_START" in preview
assert "PlaybackResourceMode.active()" in preview
assert "Rx7PreviewPolicy.allowed" in preview
assert 'startActivity(new Intent(this,DvbPlaybackActivity.class))' not in preview
assert "getString(Rx7PreviewPolicy.BOUND" in preview_settings
assert "SatelliteState.read(this)" in preview_settings
assert "validStream" in policy and "expected.equals(actual)" in policy
assert (root/'app/src/test/java/com/smarthub/plus/Rx7PreviewPolicyTest.java').is_file()
hero_policy=(source_root/'Rx7HeroArtworkPolicy.java').read_text()
hero_test=(root/'app/src/test/java/com/smarthub/plus/Rx7HeroArtworkPolicyTest.java')
assert hero_test.is_file()
assert 'shouldRefresh' in hero_policy and 'eventChanged' in hero_policy
assert 'posterHolder.addView(posterPlaceholder' in home
assert 'posterPlaceholder.setVisibility(View.VISIBLE)' in home
assert 'posterPlaceholder.setVisibility(View.GONE)' in home
assert 'channelLogoPlaceholder.setVisibility(View.VISIBLE)' in home
assert 'channelLogoPlaceholder.setVisibility(View.GONE)' in home
assert 'Rx7HeroArtworkPolicy.shouldRefresh' in home
assert 'RX7 · التلفزيون المباشر' in home
assert 'com.smarthub.plus.rx7vis701' not in preview
simple=(source_root/'Rx7SimpleUiPolicy.java').read_text()
simple_tests=root/'app/src/test/java/com/smarthub/plus/Rx7SimpleUiPolicyTest.java'
base=(source_root/'HubActivity.java').read_text()
assert simple_tests.is_file()
assert 'pinnedRows(boolean showExtras)' in simple
assert 'isAdvancedCategory(String category)' in simple
assert 'Rx7SimpleUiPolicy.isAdvancedCategory' in settings
assert 'Section.API.ordinal()' in settings and 'Section.ADVANCED.ordinal()' in settings
assert 'moreButton=settingButton' in settings
assert 'DvbPlaybackActivity.class' in settings
assert 'ReceiverDbImportActivity.class' in settings and 'PosterApiSettingsActivity.class' in settings
assert 'rx7_home_show_extras' in home and 'rx7_home_show_extras' in base
assert 'Rx7SimpleUiPolicy.pinnedRows' in home
assert 'usbAdbButton=null' in home
assert 'boolean shouldOffer=prefs().getBoolean("rx7_offer_adb_on_home",false)' in home
assert 'nav(nav,"live-feed"' in home
compat=(source_root/'Rx7Compat422Policy.java').read_text()
compat_test=root/'app/src/test/java/com/smarthub/plus/Rx7Compat422PolicyTest.java'
native_target=(source_root/'Native422OutputProfile.java').read_text()
native_test=(root/'app/src/test/java/com/smarthub/plus/Native422OutputProfileTest.java').read_text()
independent=(source_root/'Independent422Activity.java').read_text()
channel_list=(source_root/'ReceiverLibraryActivity.java').read_text()
center=(source_root/'Software422CenterActivity.java').read_text()
jni=(root/'app/src/main/cpp/native422_decoder.cpp').read_text()
buildscript=(root/'scripts/build-native422-ffmpeg.sh').read_text()
assert compat_test.is_file() and '420_450' in compat
assert 'ExtePlayer3AndroidPolicy.prefer(Native422Decoder.available()' in channel_list
assert 'String[] values=new String[13]' in native_target
assert 'height==450?800' in native_target
assert 'assertEquals(800,compatibility.width)' in native_test
assert 'Rx7Compat422Policy.fpsX100' in independent
assert 'native422_output_mode' in independent
assert 'Rx7Compat422Policy.isCaptureMissing' in independent
assert 'Native422Decoder.available()' in center
assert '4:2:2 compatibility' in center
assert 'AV_PIX_FMT_YUV420P' in jni and 'ANativeWindow_lock' in jni
assert 'FFmpeg 9.0.2' in (root/'docs/RX7_710_NATIVE422_COMPAT.md').read_text()
assert 'gpg --batch --verify source.tar.xz.asc' in buildscript
assert 'disable-gpl' in buildscript and 'disable-nonfree' in buildscript
exte=(source_root/'ExtePlayer3AndroidPolicy.java').read_text()
test_exte=root/'app/src/test/java/com/smarthub/plus/ExtePlayer3AndroidPolicyTest.java'
clock=(root/'app/src/main/cpp/exteplayer3_android_clock.h').read_text()
clock_upstream=(root/'app/src/main/cpp/vendor/exteplayer3/pts_clock.h').read_text()
audio=(root/'app/src/main/cpp/exteplayer3_android_audio.h').read_text()
bridge=(source_root/'Native422Decoder.java').read_text()
assert test_exte.is_file() and 'exteplayer3_android' in exte
assert 'pts_clock_update(&pts' in clock and 'pts_clock_update(PtsClock *clock' in clock_upstream
assert 'ExtePlayer3AndroidClock ep3Clock;' in jni
assert 'ep3Clock.paceVideo' in jni
assert 'ExtePlayer3AndroidAudio audio(' in jni
assert 'swr_alloc_set_opts2' in audio
assert 'onNativePcm' in bridge and 'AudioTrack' in bridge
assert 'selectNativeAudio()' in independent and 'toggleSkyBlueOutput()' in independent
assert (root/'app/src/main/assets/third_party/exteplayer3/GPL-2.0.txt').is_file()
assert (root/'app/src/main/assets/third_party/exteplayer3/NOTICE.txt').is_file()
assert 'swresample' in (root/'scripts/build-native422-ffmpeg.sh').read_text()
assert 'swresample' in (root/'app/src/main/cpp/CMakeLists.txt').read_text()
print('PASS: RX7 7.2.0 genuine ExtePlayer3 PTS core, ARMv7 Android FFmpeg audio/video, SkyBlue output modes')






