# IRAQhub 3.7.3 — تدقيق تكامل EPG والبوسترات والبحث و4:2:2 (2026-11-07)

## مصدر الفحص والحدود

- أحدث فرع الأب: `fix/x5m-psi-scan-channel-list-modes-20261107` (PR #16)، مبني على PR #15؛ فرع main لا يمثل الإصدار 3.7.3.
- المواصفات المرجعية: `IRAQhub_Unified_EPG_and_Poster_System_Specification (1)(1).md` التي قدمها مالك المشروع.
- مصدر DB الفعلي: `8888-20261107014804.db` (SQLite `integrity_check=ok`، 10,710 خدمة، 753 تردداً، 45 قمراً، 32 مجموعة مفضلة، 371 عضوية مفضلة).
- يحتوي TP `id=4672` على التردد غير القابل للضبط `-1928`؛ نسخة Aurora/ToSmart الاحتياطية أصلية ويجب الحفاظ على السجل وعلامة عدم الصلاحية دون تمريره إلى التونر.
- **لم تُثبت أي ميزة على رسيفر Amlogic X5M فعلياً من هذا الفرع، ولم يُنشأ APK.** الفحص والتعديلات على مستوى المصدر.

## تدقيق EPG بحسب الوثيقة

| المطلب | الحالة من قراءة الكود | الفجوة |
|---|---|---|
| DVB EIT | كود `BroadcastEpgProvider` و`DvbEpgRefreshWorker` | يقرأ أحداث الخدمة النشطة المتحقق منها؛ ليس استيراد EPG لكل خدمات القنوات |
| XMLTV وEPGShare | `EpgShareFeedDirectory` و`StreamingXmlTv` و`OnlineEpgGuide` | اكتشاف الملفات والتحميل المضغوط والتطابق اليدوي متاح في الكود؛ الافتراضي 3 خلاصات فقط، ولا يوجد إثبات تحميل قاعدة جميع القنوات |
| EPG Pro | إشارات إلى واجهة OEM والتكامل | لا يوجد إثبات قراءة EPG Pro لكل القنوات بلا صلاحيات المصنع |
| Sky Italia | مصدر اختياري في المواصفات | لا يوجد تحقق من تشغيل التكامل الإنتاجي |
| محرك تطابق القنوات | آليات أسماء/هويات وقنوات في عدة طبقات | ليس محرك SQLite موحداً شاملاً للجدول `epg_channel_mappings` |
| قاعدة SQLite EPG موحدة | بعض مخازن EPG تعتمد JSON وGZIP/Cache | جداول `epg_sources` و`epg_programmes` وغيرها الواردة في الوثيقة غير مثبتة كاملة |
| 4 ساعات | `IraqHubSyncScheduler` يستخدم 240 دقيقة في WorkManager | جدولة تحت سيطرة Android وقد تتأخر؛ لم تختبر استعادة جميع المهام بعد إقلاع X5M |
| Infobar الآن/التالي | `LocalInfoBarController` + `NowNextView` | ربط برمجي قائم؛ يلزم التحقق من أحداث EPG حقيقية وتوقيت وصور صحيحة |

## تدقيق Poster Engine

| المطلب | الحالة من قراءة الكود | الفجوة |
|---|---|---|
| TMDB | `TmdbProvider` (يتطلب مفتاحاً من المستخدم) | لا يوجد اختبار تنزيل وعرض على الرسيفر لهذا الفرع |
| TVmaze | `TvmazeProvider` | يعتمد المطابقة الموثوقة/الحلقة المحددة، وبعض العناوين قد تفشل بصورة صحيحة |
| BetterPosters | Manifest ديناميكي في `BetterPostersProvider` + direct IMDb | صلاحية الـManifest لا تضمن صلاحية كل صورة |
| TheSportsDB | `TheSportsDBProvider` ومعرّفات `tsdb:...` | تغطية الخطة المجانية جزئية، وليست خدمة نتائج مباشرة شاملة |
| Wikimedia Commons | `CommonsArtworkProvider.exactFile` | مرتبط بملف محدد متحقق منه؛ ليس اكتشافاً تلقائياً لكل برامج EPG |
| مطابقة محتوى البرامج | `PosterSelection` و`UnifiedArtworkPrefetch` | لا توجد قاعدة SQLite موحدة كاملة `poster_content_mapping` كما في الوثيقة |
| الرياضة بالـInfobar | `EpgArtwork` و`SportsProgrammeArtwork` | دعم الحالي والقادم وبطاقة شعارات المصدر في الكود؛ لا دليل جهاز فعلي |
| Offline Cache أثناء 4:2:2 | `EpgArtwork.loadCached` | الآن يقرأ الصور المخزنة دون شبكة؛ يلزم قياس ذاكرة/إطارات X5M |
| تحديث المباريات تلقائياً | `PosterRefreshWorker` يبدأ تحديث `MatchRepository` وprefetch بعد وصوله | الشبكة ومصادر المباريات قد تفشل؛ تم وضع حد لطلبات الخلفية أثناء DVB |
| سرعة صورة أقل من 1 ثانية | `ArtworkCache` يقدم صوراً محلية | هدف تصميم غير مقاس على الجهاز |
| الترخيص | بيانات Wikimedia، بعض metadata وcredits محفوظة | تدقيق تراخيص كل مصدر وإعادة توزيع الصور ما زال مطلوباً |

**النتيجة: مواصفات البوسترات لم تنفّذ بالدقة الكاملة، ولا يمكن اعتمادها بعد.** لا يجوز الخلط بين اختبارات Java بهويات حقيقية وبين فحص الصور المعروضة على شاشة الرسيفر.

## CHMAX وملفات القنوات

- `CHMAXIntegration` يستخرج شعار قناة من Aurum عندما تتاح واجهة Accessibility وشاشة أصلية مرئية؛ ليس استخراجاً مباشرة من `/data/ChannelImgs`.
- `AurumDvbLogoBridge` يشترط تطابق الاسم والتردد وهوية الخدمة قبل إعادة استخدام شعار؛ النسخة الحالية تتجاوز الصور التالفة/القديمة وتكتب الصورة الملتقطة إلى Cache بصورة ذرية.
- `LogoSourcesActivity` يستقبل SQLite `.db` وCHL باستخدام منتقي Android؛ `ReceiverSqliteImport` يدعم صيغة IRAQhub و`ReceiverOemSqliteImport` يدعم تصدير ToSmart/Aurum.
- الملف الاحتياطي المختبر سليماً يخلو من سجلات أيتام/ترددات خارج الحدود المسموحة لاستيراد OEM، لكن لا يوجد اختبار Android لاستيراده كاملاً ثم تشغيل القنوات منه.

## القناتان اليدويتان وواجهتا Aurum

- `DirectTransponderScanner`: ضَبط تردد فعلي + محاولة `tp_record=1` + PAT/PMT/SDT مع تحقق CRC؛ الآن يستخدم فحص TS نهائياً ويُميّز النتائج غير المكتملة.
- **مانع الأجهزة:** قد يرفض firmware طلب whole-TP، ولا يكفي تحليل DEX لإثبات صلاحية استدعاء Binder/SDK من APK عادي.
- `ReceiverLibraryActivity`: وضعا DVB وAurum أصبح لهما إجراءات وظيفية مختلفة: عرض EPG للعنصر المحدد مقابل تردد العنصر وبدء البحث/الفلترة بالاسم؛ ليست مجرد زيادة عرض النافذة.
- المصدر الأصلي يضم `com.tosmart.dvb.common.home.channellist.ChannelListFragment` و`com.tosmart.aurum.home.channelList.ChannelListFragment`. تطابق جميع أحداث الريموت وEPG/PVR/CA، وحركات التنقل يتطلب تفكيك أجسام دوال DEX واختبار OEM؛ **لم يُثبت بعد**.

## اختبار قبول مطلوب قبل الدمج/الإصدار

1. استيراد ملف DB المرفق من واجهة `LogoSourcesActivity` والتحقق من عدد 10,710 قناة وجداول المفضلة، وعرض تحذير عند محاولة تشغيل TP `-1928`.
2. إجراء بحث مباشر على TP فعلي وتسجيل كود الخطأ `FIRMWARE_FULL_TS_CAPTURE_REJECTED` أو `FIRMWARE_CAPTURE_NO_TS_DATA` أو الأعداد وأسماء القنوات الناتجة.
3. مقارنة وضعَي القائمة مع Aurum بالريموت: OK/BACK/INFO/الألوان/صفحات القنوات، وبيانات EPG للقناة المحددة.
4. أثناء قناة فيلم ومسلسل ومباراة: إثبات معرّفات المحتوى، صورة الحدث الحالي والقادم في Infobar، ومدة العرض ومصدر كل صورة وحالات الخطأ.
5. اختبار CHMAX بشاشة Aurum الأصلية والتأكد من `chmax_observe_status` و`native_logo_bridge_status`، ثم التحقق من شعار نفس قناة DVB.
6. تشغيل فيديو حقيقي 4:2:2 (HEVC/AVC) مدة طويلة، وتسجيل التقطيع وإطارات الديكودر والذاكرة، ومقارنة الصور المحلية قبل/خلال/بعد التشغيل.
7. قطع الإنترنت ثم إعادة تشغيل الرسيفر وتجربة الاستعادة من Cache؛ التحقق من جدولة EPG/XMLTV والبوسترات كل 240 دقيقة مع سماحية تأخير نظام Android.
8. لا إعلان نجاح CI أو بناء APK من مجرد فتح PR. نشر النتائج الدقيقة بعد تشغيل الاختبارات.

## ملفات هذا الإصلاح

- `EpgArtwork.java`، `LocalInfoBarController.java`، `SportsProgrammeArtwork.java`، `SportsPosterPrefetch.java`
- `PosterRefreshWorker.java`، `MatchRepository.java`
- `CHMAXIntegration.java`، `SoftwareVideoActivity.java`
- `DirectTransponderScanner.java`، `ManualTransponderActivity.java`، `ReceiverLibraryActivity.java`
- `SportsProgrammeArtworkTest.java`

كل التغييرات في فرع تطوير منفصل؛ لا تعدّل قاعدة Aurum المحمية ولا تضع واجهة وهمية مكان OEM.