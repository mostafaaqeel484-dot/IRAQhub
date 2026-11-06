package com.smarthub.plus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Actual remote mappings, not a decorative help page. */
public final class RemoteHelpActivity extends HubActivity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout body=page(tx("لوحة المساعدة · IRAQhub","IRAQhub remote help"),
    tx("اختصارات الريموت حسب شاشة التشغيل وقائمة القنوات، وحلول المشاكل الشائعة.",
       "Remote shortcuts for live TV/channel list, and quick fixes."));
  LinearLayout tv=card(body);
  tv.addView(text(tx("أثناء مشاهدة القنوات","While watching DVB"),22,true));
  note(tv,tx("CH+ / CH−: القناة التالية / السابقة\n↑ / ↓: تبديل القناة\nOK: قائمة القنوات\nINFO أو الأزرق: شريط المعلومات\nBACK: رجوع\nالأحمر: المسارات الصوتية\nGUIDE: دليل EPG\nVOL±: الصوت",
    "CH+/CH−: Next/previous channel\nUp/Down: Change channel\nOK: Channel list\nINFO or BLUE: Infobar\nBACK: Back\nRED: Audio tracks\nGUIDE: EPG\nVOL±: Volume"));
  LinearLayout channels=card(body);
  channels.addView(text(tx("داخل قائمة القنوات","In channel list"),22,true));
  note(channels,tx("↑ / ↓: تحريك التحديد\nOK: تشغيل القناة\nCH+ / PAGE UP: صفحة للأعلى\nCH− / PAGE DOWN: صفحة للأسفل\nاللون الأحمر: المفضلة\nالأخضر: نوع القنوات\nالأصفر: الفرز\nالأزرق: الإشارة\nالضغط الطويل OK على القناة: تفاصيلها",
    "Up/Down: Move focus\nOK: Tune selected channel\nCH+/PAGE UP: Page up\nCH−/PAGE DOWN: Page down\nRED: Favourites\nGREEN: Channel type\nYELLOW: Sort\nBLUE: Signal\nLong-press OK: Channel details"));
  LinearLayout tips=card(body);
  tips.addView(text(tx("الحلول السريعة","Quick fixes"),22,true));
  note(tips,tx("إذا ما اشتغل التردد: افتح ضبط LNB؛ أقمار Ku تستخدم 9750/10600 افتراضياً. إذا ما ظهر شعار: اختبره بمصادر الشعارات. إذا ما طلع بوستر: تأكد من معلومات EPG والمفتاح أو هوية IMDb/TMDB؛ ما نستبدل بوستر صحيح بصورة عشوائية.",
    "For no tuner lock: check LNB (Ku defaults to 9750/10600). For missing channel logos: check logo sources. Missing posters require a valid EPG programme and verified content identity; no random artwork is assigned."));
  note(tips,tx("BISS: افتح القناة واضغط زر BISS الظاهر أسفل الشاشة، أو F3 إذا كان مدعوماً. البحث اليدوي من التركيب ويظل منفصلاً عن BISS.",
   "BISS: press the on-screen BISS button or F3 on supported remotes. Manual search has a separate Installation screen."));
  action(tips,tx("البحث اليدوي","Manual satellite scan"),()->startActivity(new Intent(this,ManualTransponderActivity.class)));
  action(tips,tx("إعداد LNB","LNB settings"),()->startActivity(new Intent(this,TunerSettingsActivity.class)));
  action(tips,tx("إصلاح شعارات القنوات","Channel logo sources"),()->startActivity(new Intent(this,LogoSourcesActivity.class)));
  action(tips,tx("دليل EPG","Programme guide"),()->startActivity(new Intent(this,SatelliteGuideActivity.class)));
  action(tips,tx("الإعدادات المتقدمة","Advanced settings"),()->startActivity(new Intent(this,AdvancedSettingsActivity.class)));
 }
}
