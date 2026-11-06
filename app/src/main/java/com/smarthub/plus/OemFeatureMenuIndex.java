package com.smarthub.plus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Public parent-menu routes and OEM navigation-graph evidence; never private fragment launching. */
final class OemFeatureMenuIndex {
 private OemFeatureMenuIndex(){}
 static final class Item {
  final String group,ar,en,route,graph,fragment;
  final boolean confirmed,writes;
  Item(String line){
   String[] x=line.split("\\|",-1);
   if(x.length!=8)throw new IllegalArgumentException(line);
   group=x[0];ar=x[1];en=x[2];route=x[3];graph=x[4];fragment=x[5];
   confirmed="1".equals(x[6]);writes="1".equals(x[7]);
  }
  String label(boolean arabic){return arabic?ar:en;}
 }
 private static final String[] ROWS={
  "SCAN|تركيب وبحث الأقمار|Satellite installation|xml:installation|nav_installation|SearchChannelFragment|1|0",
  "SCAN|بحث سريع Fast Scan|Fast Scan|xml:installation|nav_satellite|SatelliteFastScanFragment|1|1",
  "SCAN|بحث يدوي Manual Scan|Manual Scan|xml:installation|nav_satellite|SatelliteManualFragment|1|1",
  "SCAN|بحث أعمى Blind Scan (إن توفر)|Blind scan (if available)|xml:installation|nav_satellite|SatelliteManualFragment|0|1",
  "SCAN|بحث الشبكة Network Scan (إن توفر)|Network search (if available)|xml:installation|nav_satellite|SatelliteManualFragment|0|1",
  "SCAN|بحث متعدد الأقمار (إن توفر)|Multi-satellite scan (if available)|xml:installation|nav_satellite|SatelliteFragment|0|1",
  "SCAN|بحث DVB-T الأرضي|DVB-T search|xml:installation|nav_terrestrial|TerrestrialFragment|1|1",
  "SCAN|بحث DVB-T يدوي|DVB-T manual search|xml:installation|nav_terrestrial|TerrestrialManualFragment|1|1",
  "SCAN|بحث DVB-C الكابل|DVB-C search|xml:installation|nav_cable|CableFragment|1|1",
  "SCAN|بحث DVB-C يدوي / NID|DVB-C manual/NID scan|xml:installation|nav_cable|CableManualFragment|1|1",
  "DISH|إعدادات LNB المحلية|IRAQhub LNB / DiSEqC profiles|local_lnb|||1|1",
  "DISH|إضافة الأقمار وترددات TP|Satellites and transponder editor|xml:installation|nav_satellite|SatelliteFragment|1|1",
  "DISH|تعديل PID|Edit service PIDs|xml:installation|nav_installation|EditPidFragment|1|1",
  "DISH|USALS والموقع الجغرافي|USALS motor and location|xml:installation|nav_satellite|SatelliteUsalsFragment|1|1",
  "DISH|محرك DiSEqC 1.2|DiSEqC 1.2 motor|xml:installation|nav_satellite|SatelliteMotorFragment|1|1",
  "DISH|Unicable / SCR (إن توفر)|Unicable / SCR (unverified)|xml:installation|nav_satellite|SatelliteAntennaFragment|0|1",
  "DISH|Satellite Finder|Satellite Finder|xml:installation|nav_installation|SatelliteFinderFragment|1|0",
  "DISH|TKGS وتحديث القنوات|TKGS channel service|xml:installation|nav_satellite|SatelliteTKGSFragment|1|1",
  "CHANNEL|تحرير قنوات TV/Radio|Edit TV / radio|xml:channel|nav_channel|EditChannelFragment|1|1",
  "CHANNEL|نقل وإعادة تسمية القنوات|Move / rename channels|xml:channel|nav_channel|EditChannelFragment|1|1",
  "CHANNEL|حذف القنوات|Delete channels|xml:channel|nav_channel|DeleteChannelFragment|1|1",
  "CHANNEL|فرز وترتيب القنوات|Sort channels|xml:channel|nav_channel|SortChannelFragment|1|1",
  "CHANNEL|المفضلة الأصلية|OEM favourites|xml:channel|nav_channel|EditFavFragment|1|1",
  "CHANNEL|ألوان القنوات|Channel colour tags|xml:channel|nav_channel|ChannelColorFragment|1|1",
  "CHANNEL|قفل / تخطي القنوات (إن توفر)|Lock / skip (if supported)|xml:channel|nav_channel|EditChannelFragment|0|1",
  "CHANNEL|تحديد قناة بدء الجهاز|Startup channel|xml:channel|nav_channel|StartChannelFragment|1|1",
  "CHANNEL|حفظ القنوات على USB|USB channel backup|xml:channel|nav_channel|UserChannelListFragment|1|1",
  "CHANNEL|استرجاع القنوات من USB|Restore channels from USB|xml:channel|nav_channel|UserLoadFragment|1|1",
  "CHANNEL|قنوات ومفضلة IRAQhub|IRAQhub channels and favourites|local_channels|||1|0",
  "CHANNEL|قائمة أقمار CHL المستوردة|Imported CHL satellites|local_sats|||1|0",
  "CLOUD|ربط CHMAX وتحديث EPG|CHMAX connection and EPG|local_chmax|||1|0",
  "CLOUD|حفظ/استعادة القنوات سحابياً|OEM cloud list save / restore|xml:channel|nav_channel|UserChannelListFragment|1|1",
  "CLOUD|تحديث CHMAX التلقائي (من OEM)|OEM CHMAX auto-update settings|xml:other|||0|1",
  "CLOUD|شعارات CHMAX عبر CHL|CHL/CHMAX logo add-on|local_logos|||1|0",
  "RECORD|مؤقت البرامج والتسجيل|OEM event timer|timer|||1|1",
  "RECORD|تسجيل مباشر من ريموت Aurum|REC in native TV|tv|||1|1",
  "RECORD|TimeShift من ريموت Aurum|TimeShift in native TV|tv|||1|1",
  "RECORD|مكتبة PVR الداخلية (غير مصدرة)|Private PVR library (not exported)|private_pvr|||1|0",
  "RECORD|وسائط USB والتسجيلات|USB files and recordings|usb_files|||1|0",
  "TV|التلفاز الأصلي|Original Live TV|tv|||1|0",
  "TV|EPG Pro الأصلي|Original EPG Pro|epg|||1|0",
  "TV|صوت وترجمة وتيليتكست|OEM audio/subtitles/teletext|tv|||1|0",
  "TV|فحص إشارات التونر|Live signal diagnostics|local_signal|||1|0",
  "TV|معلومات Infobar ومظهره|Infobar appearance|osd|||1|0",
  "TV|اختبار الريموت|Remote key tester|remote|||1|0",
  "OTHER|Server Settings|Server Settings|server|||1|0",
  "OTHER|CA Menu|CA Menu|ca|||1|0",
  "OTHER|Patch/BISS Menu|Patch/BISS Menu|patch|||1|1",
  "OTHER|Home Share|Home Share|homeshare|||1|0",
  "OTHER|مشغل USB الأصلي|OEM USB media player|usb_media|||1|0",
  "OTHER|إعدادات الشبكة|OEM network settings|xml:network|||1|0",
  "OTHER|قائمة الوظائف الأخرى|Other OEM features|xml:other|||1|0",
  "OTHER|إعدادات النظام|Android settings|android_settings|||1|0",
  "OTHER|تحديث النظام بالشبكة|Firmware network update|net_update|||1|1",
  "OTHER|تحديث النظام بملف USB|Firmware USB update|file_update|||1|1",
  "OTHER|إدارة التطبيقات|Original app manager|all_apps|||1|0",
  "OTHER|خلفيات Aurum|Aurum wallpapers|wallpaper|||1|0",
  "OTHER|واجهة Aurum الأصلية|Aurum Home|home|||1|0"
 };
 static List<Item> all(){
  List<Item> result=new ArrayList<>();
  for(String row:ROWS)result.add(new Item(row));
  return Collections.unmodifiableList(result);
 }
 static int count(String group){int n=0;for(Item i:all())if(i.group.equals(group))n++;return n;}
}
