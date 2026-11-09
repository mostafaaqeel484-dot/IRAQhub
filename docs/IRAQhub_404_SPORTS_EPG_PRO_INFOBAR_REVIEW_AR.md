# IRAQhub 4.0.4 — تدقيق الرياضة وEPG Pro وInfoBar والبحث والأنتينا و4:2:2
> الملحق الهندسي لخطة `IRAQhub_4.0.4_FULL_CODEX_PLAN_AR.md`، 2026-11-09. هذا **مطلوب إصلاحه واختباره**، وليس ادعاء إنجاز عملي على X5M.

## ما فُحص فعلياً
- سورس GitHub 4.0.4: `MatchRepository.java`, `SportsProgrammeArtwork.java`, `SportsPosterPrefetch.java`, `EpgArtwork.java`, `TheSportsDBProvider.java`, `LocalInfoBarController.java`, `OsdSettingsActivity.java`, `SatelliteGuideActivity.java`, `DvbEpgRefreshWorker.java`, `ManualTransponderActivity.java`, `DvbTuner.java`, `TunerSettingsActivity.java`, `ReceiverAntennaMenuActivity.java`.
- `IRAQhub_4.0.2_Logo_Android_Assets.zip`: شعارات التطبيق وأيقوناته فقط، **ليس أرشيف كود 4.0.4 أو قاعدة شعارات CHMAX**.
- `AurumDvbProject_Source(1).zip`: Bridge لعناوين مكونات OEM المصدّرة، وليس سورس Aurum الأصلي.
- `IRAQhub_Manual_Fast_Blind_Scan_ADB_Probe.zip`: سكربت جمع ولا يحتوي سجلات فحص حقيقية.
- `IRAQhub_scan_probe_20261106_202525.zip`: يحوي 42 مدخلاً بينها صور/XML/Shell/logcat لخطوات Aurum Manual/Fast/Blind. الأنشطة من `com.tosmart.dvb.common.NavHostActivity`؛ السجلات تحوي `dvbstack`, `STBHWTUN` و`sdt section`. لا يعني ذلك أن UID تطبيق عادي يمكنه فتح `/dev/dvb*`.
- مواصفات EPG/Poster وAurumReverse README التاريخية: مرجع سلوك، ليست إثبات تنفيذ.
- مصدر 4.0.4 هو فرع Codex/PR #46؛ لا تفترض أنه مدموج في main أو أن ZIP الأقدم نسخة حديثة. بعض أرشيفات IRAQhub الكبيرة القديمة ظهرت في Library لكن لم يتوفر فحص بايتاتها؛ لا تصفها بأنها فُحصت بالكامل.

## نتيجة 1 — سبب اختفاء البوسترات الرياضية
1. `EpgArtwork.sportsMatch()` يتوقف حين لا يوجد `startMs/endMs` صالحان؛ `MatchRepository.selectForProgramme()` يحتاج Fixture متطابقاً بفارق وقت محدود وباسمَي الفريقين.
2. `SportsProgrammeArtwork.verified()` يشترط event ID وteam IDs ودورياً وkickoff. السلوك الحالي عند غياب Fixture هو عدم إنتاج بطاقة VS أصلاً.
3. `MatchRepository.tokens()` يعالج Man/Utd/Barca لكنه لا يطابق `C.plas` أو `C. Palace` مع Crystal Palace.
4. يجب الحفاظ على بطاقة `SportsProgrammeArtwork.card()` الحالية للمباريات الموثقة وتوسيع مسار بديل **team-only** لا يعتمد على وجود المباراة.

### المطلوب لسطر EPG: `Liverpool vs c.plas`
- فك العنوان إلى Liverpool FC وCrystal Palace مع قاموس aliases معروف، والتثبت من معرف النادي (TheSportsDB V1 `searchteams.php?t=` أو ID مخزّن موثوق).
- إن وجد بوستر Match رسمي متطابق: هو الأولوية. إن غاب ولكن Fixture موثق: رسم بطاقة VS بشعارين.
- **إن لم توجد مباراة مسجلة أصلاً**: اعرض شعار نادي Liverpool **يساراً** وشعار نادي Crystal Palace **يميناً** في واجهة InfoBar، وفاصلاً VS، مع وسْم `الفرق مستنتجة من عنوان EPG؛ المباراة غير متحققة`.
- وقت الحدث من EPG لا يُسمّى موعد kickoff إذا لم يتأكد من المزود. لا تختلق النتيجة أو البطولة.
- لو توافر شعار واحد: اعرض الآخر باسم النادي/أحرف محايدة؛ لا تبحث عن شعار مشابه غير موثوق.
- عند عدم وجود شعارات استخدم بطاقة نصية محايدة، وليس بوستر فيلم أو صفحة فارغة.
- أظهر الشعارين كصورتين مستقلتين على جانبي `NowNextView`، مع دعم RTL، وبوسترات NOW/NEXT الأخرى في مكانها.
- اضبط cache keyed by verified team ID/URL، Prefetch أثناء هدوء الجهاز. في 4:2:2 cached-only للحفاظ على الإطارات؛ عند HTTP 429 احترم backoff/negative cache.
- لا تخلط `CHMAX channel logos` مع `football team crests`. لا تطلب مفاتيح Premium V2 إن لم يوفّرها المستخدم.
- صحح `OnlineProgramme` حتى لا يحاول تحويل عنوان مواجهة رياضية إلى بوستر فيلم مشابه.

**اختبارات:** `Liverpool vs c.plas`, `Liverpool vs Crystal Palace`, `Crystal Palace v Liverpool`, `Man Utd vs Liverpool`, `Palace` ambiguous، مباراة مستقبلية بلا Fixture، صور فريق واحدة/اثنتان/صفر، NOW/NEXT المتكرر، channel switch، cached-only مع 4:2:2.

## نتيجة 2 — مدة InfoBar 1 إلى 15 ثانية
- الحالي: `OsdSettingsActivity` يضع `1–10` ويرفض >10، و`LocalInfoBarController.show()/showDetails()` يحصران المدة بـ `Math.min(10,...)`.
- أصلح **الإعداد والحفظ والتطبيق معاً**؛ انقل الـclamp إلى helper موحد `[1,15]` وافحص كل قرّاء `osd_duration_seconds`.
- كل استدعاء INFO يعيد احتساب إغلاق الشريط دون تأثر timer قديم؛ زر BACK/INFO يعكس السلوك المطلوب. لا تربط حالة `No Signal` بخفوت OSD المؤقت.
- اختبر 1،5،10،11،15 ثانية على X5M، والحالات expanded/small/عند تغيير قناة.

## نتيجة 3 — EPG EIT وEPG Pro
- `DvbEpgRefreshWorker` يقرأ EIT من خدمة tuner عند lock؛ `UnifiedEpgIndex.recordNative` أصلح خطأ فصل event key عن channel ID في PR #46؛ يجب إثباته على الجهاز.
- `SatelliteGuideActivity.nativeGuide()` يرسل `com.tosmart.action.epg_pro` لفتح واجهة OEM؛ هذا **ليس** استيراداً للبيانات من EPG Pro.
- أضف دليل Pro داخل IRAQhub من Native DVB EIT + XMLTV + published EPG إن كانت قراءته مصرحاً بها؛ توحيد الهوية/الأوقات UTC وتوقيت العرض المحلي، مع وضع مصدر الحدث ودرجة حداثة البيانات.
- NOW/NEXT سريع عند تغيير القناة من cache ثم المصدر الأصلي، بلا انتظار الجلب الشبكي للبوسترات. استمر بآخر EPG سليم إذا فشل التحديث.
- تحديث XMLTV دوري في الخلفية نحو كل 4 ساعات حسب وثيقة المواصفات، مؤجل عند ضغط 4:2:2، بلا إعادة تحليل كاملة بلا حاجة.
- اختبارات اختلاف توقيت، حدث بنفس العنوان بزمن مختلف، قناة تبدلت أثناء fetch، EIT بدون lock، XMLTV دون EPG native، no duplicate، بيانات قديمة.

## نتيجة 4 — البحث اليدوي والأنتينا
- `ManualTransponderActivity` يملك `DirectTransponderScanner` وPAT/PMT/SDT لكنه لم يثبت أخذ TS من OEM على X5M؛ التشخيص السابق: `FIRMWARE_CAPTURE_NO_TS_DATA`, `NO_COMPLETE_PSI`, `services=0`.
- `TunerSettingsActivity` يخزن إعدادات LNB؛ الحفظ لا يضمن ضبط التونر فعلياً. `DvbTuner` ينفّذ ضبطاً عند وجود الواجهة ويقيس lock ويتعامل مع USALS عند توفره.
- أضف مراحل تشخيص `config_saved → tune_command → tuner_lock → ts_bytes → PAT → PMT → SDT → channels_imported`. قارن مع Probe ZIP وOEM stack دون تجاوز أذونات النظام.
- اختبر التردد/القطبية/SR، LNB 9750/10600، 22kHz، DiSEqC 1.0/1.1، USALS stop وUnicable عند توفر SDK، حفظ ملف مستقل لكل قمر، PVR safety، backup.
- لا تعلن نجاح البحث ما لم تضف خدمات SID/PMT/PIDs موثقة قابلة للتشغيل.

## نتيجة 5 — 4:2:2 والرسوم
- أصلح تجمد أول إطار/شاشة سوداء مع استمرار الصوت؛ اعزل أخطاء HEVC RPS/POC/TS continuity والـJNI send/receive EAGAIN والـSurface.
- راقب `decode_fps` **و`render_fps` الحقيقي** وinput bitrate والـCC/TEI والأخطاء؛ decode FPS وحده لا يثبت تقدّم الصورة.
- الإعداد Auto/720/540/360/Lite، من دون ادعاء أن downscale بعد decode يجعل HEVC 1080p أرخص. اعمل prefetch للشعارات قبل تشغيل محرك ثقيل وcached-only أثناءه.
- قبول 10 دقائق حركة فيديو فعلية على X5M ARMv7 مع تبديل القنوات وعودة Surface، وفحص ملف APK النهائي بعد التوقيع والتنزيل.

## مراحل العمل لـCodex
- PR1: InfoBar duration 1–15 (helper+tests).
- PR2: sports title/parser/verified team crests/cache/2-side view (tests).
- PR3: EPG Pro/Native EIT/XMLTV fast guide (tests).
- PR4: manual DVB PSI and antenna/USALS diagnostics (tests and X5M).
- PR5: native 4:2:2 render health/stability (tests and X5M).
- لا تحذف وظائف Tiger RX أو BISS وCHMAX وPVR أو تُغير main دون مراجعة. لكل PR: السبب المثبت، الملفات، نتائج JVM، اختبار الجهاز، وما لم يثبت بعد.

## مراجع مزود البيانات
- TheSportsDB: https://www.thesportsdb.com/docs/api
- V1 guide: https://www.thesportsdb.com/docs_api_guide
- V1 المجاني محدود بالنتائج وعدد الطلبات، ولا يعِد بصورة رسمية لكل مباراة؛ غياب Poster شائع ويجب أن ينتهي بشعارَي الفريقين لا شاشة فارغة.
