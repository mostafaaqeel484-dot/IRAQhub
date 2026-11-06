# IRAQhub 3.7 — المرحلة الثانية: اكتشاف DVB + EPG/Poster الموحد

## مصدر المتطلبات

`IRAQhub_Unified_EPG_and_Poster_System_Specification (1).md` الذي رفعه المستخدم، مقارنة ببنية فرع `feature/iraqhub-370-native-motor-vlc-20261106` وبالفحص السابق لـ `com_2.tosmart.aurum_1.0.apk`.

## الوظائف المُضافة في كود IRAQhub

### اكتشاف خدمات DVB وحفظها

- `DvbPsiScan.java`: قارئ MPEG-TS بحد 64 MB لكل جلسة، يفحص PAT/PMT/SDT ويتحقق من CRC لكل section، ثم يحدد SID وTSID وONID وPMT/PCR/Video/Audio PID، نوع فيديو H.264/H.265/MPEG2 وصوت AAC/AC3 وغير ذلك وCAID إن وجد. يدعم أسماء DVB بترميزات متعددة ومنها العربية حيث تتوفر تسمية محكمة.
- `TsServiceImportActivity.java`: اختيار القمر والتردد الحقيقيين من قوائم IRAQhub ثم اختيار ملف TS عبر Android Storage Access Framework أو تسجيل PVR محفوظ داخل التطبيق؛ يعرض معاينة الخدمات قبل تأكيد الاستيراد. الملف يجب أن يكون من التردد المختار — لا يستنتج التردد من اسم القناة أو من ملف TS.
- `ReceiverCatalog.savePsiServices`: إضافة خدمات جديدة مكتملة فقط، مع تجاهل المكررات، ضمن transaction في SQLite الخاص بـIRAQhub؛ لا يستبدل القنوات الحالية أو المفضلة، ولا يكتب إلى Firmware database.
- مداخل قائمة التركيب في `ReceiverToolsActivity` و`InstallationEditorActivity`.
- `DvbPsiScanTest`: اختبارات مصنّعة للجداول وCRC؛ **هذه ليست تجربة رسيفر فعلي**.

**الحد الدقيق:** لا يوجد اتصال مخوّل بعد بواجهات `com.dvb.search.SearchManager` المحمية في Aurum، لذلك **Blind Scan/Network Scan مباشر من التونر وحفظ قنوات جديدة في قاعدة المصنع غير مكتمل**. المسار المضاف هو اكتشاف خدمات أصلية *من تسجيل TS متاح* وحفظها في قاعدة IRAQhub الخاصة.

### منظومة EPG والبوسترات بحسب المواصفات

- `IraqHubSyncScheduler`: ثلاثة أعمال مستقلة كل 240 دقيقة: تنزيل XMLTV مع اتصال شبكة، تحديث BetterPosters/الصور الموثقة مع اتصال شبكة، وقراءة DVB EIT للقناة النشطة المؤكدة دون اشتراط اتصال الإنترنت.
- `IraqHubSyncWorker` و`PosterRefreshWorker` و`DvbEpgRefreshWorker`: فصل فشل كل مصدر عن الآخر، وإرجاء المهام الثقيلة عند تشغيل فك 4:2:2 أو ضبط التونر أو تسجيل PVR. قراءة DVB EIT لا تضبط التونر ولا توقف البث.
- `EpgShareFeedDirectory`: اكتشاف فعلي لأسماء `epg_ripper_*.xml.gz` من فهرس HTTPS، مع cache عند انقطاع الإنترنت. `OnlineEpgGuide`: اختيار المصادر المحددة والتحديث عبر GZIP، استخدام ETag / Last-Modified كما في المسار السابق، مع تحميل محدود وقراءة XMLTV بدفق SAX عند طلب قناة محددة.
- `SatelliteGuideActivity`: شاشة اختيار مصدر XMLTV للقناة النشطة بناءً على هويتها الفيزيائية، مع احترام معرّف XMLTV اليدوي؛ وعرض أوقات نجاح/فشل المزامنة، وأعداد أحداث DVB الفعلية المرصودة، وحالة BetterPosters وزر تحديث مستقل.
- `BetterPostersProvider`: مسارات Stremio catalog/meta مشتقة من قاعدة Manifest الحقيقي المعطى في المواصفات (بدلاً من افتراضها في جذر btttr.cc).
- `UnifiedArtworkPrefetch`: للبرنامج الحالي والتالي فقط مع TMDB ID مختار وموثوق للمحتوى. يتحقق من استجابة metadata والصورة وأبعادها، ويُسجّل ارتباط الصورة بالحدث المحدد بالوقت والقناة؛ صورة صحيحة سابقة محفوظة عند الفشل.
- `LocalInfoBarController`: استرجاع الصور الموثوقة من Cache بصفتها fallback عند غياب مصدر أقوى، دون تغيير شكل Infobar أو البوسترات أو HOME.

## وظائف المواصفات التي لا يجوز وصفها بالمكتملة

1. لم تُثبَت قراءة DVB EPG لجميع قنوات النظام دفعة واحدة: المصدر الحالي للقناة النشطة المعروفة فقط.
2. ملفات XMLTV التي تتجاوز حد التنزيل الحالي (10 MB مضغوطة) تحتاج طريقة تخزين/Streaming إضافية، وليس تحميل كل XML في RAM. لا ندعي دعم ALL_SOURCES1 الضخم.
3. جداول SQLite المنطقية الكاملة لـ EPG/Posters مثل `epg_sources` و`poster_content_mapping` وربط كل برنامج بصورة بجدول مفاتيح أجنبية ما زالت مشروع ترحيل تدريجي؛ لم نحذف قواعد البيانات/Cache السابقة.
4. EPG Pro وSky Italia خاصان بمصادر مدعومة محددة ولم يُؤكَّد توفر API لكل القنوات، لذلك لم نختلق بيانات لهما.
5. BetterPosters manifest/metadata والصور ومصادر TMDB/TVmaze/TheSportsDB ما زالت تحتاج طلبات حقيقية وفحص الجهاز لتأكيد التشغيل والجودة والحقوق والحصص.
6. معيار سرعة البوسترات المحلية أقل من ثانية ومعيار عدم تقطيع 4:2:2 يحتاجان قياس الجهاز؛ النجاح البرمجي لا يكفي.
7. لا يوجد وصول ADB USB إلى رسيفر المستخدم في هذه البيئة، لذلك اختبارات التونر/الموتور والتسجيل وفك الفيديو والـEPG الفعلي غير منفذة هنا.

## بيانات التحقق والقيود

- مصدر EPGShare: https://epgshare01.online/epgshare01/ — يحتوي فعلاً على ملفات GZIP متعددة، بعضها كبير وبعضها قديم. التطبيق يكتشف القائمة بدلاً من نسخ أسماء الملفات عشوائياً.
- مصدر BetterPosters: عنوان manifest الكامل في المواصفات؛ التطبيق يتحقق من الهوية والموارد والإصدار، ويفشل دون اختلاق نتائج.
- الاختبارات المتوفرة: اختبارات وحدات صناعية ونجاح/فشل Gradle CI؛ لا تتضمن أجهزة حقيقية ولا أرقام قنوات مسحوبة من رسيفر فعلي.
- لا تغير هذه المرحلة توقيع APK الأصلي، ولا تعطل Aurum النظامي؛ نسخ Debug غير مناسبة للتثبيت فوق تطبيق موقع بمفتاح مختلف من دون نسخة احتياطية.

## ملفات جوهرية مُعدّلة

`DvbPsiScan.java`, `TsServiceImportActivity.java`, `ReceiverCatalog.java`, `ReceiverToolsActivity.java`, `InstallationEditorActivity.java`, `IraqHubSyncScheduler.java`, `IraqHubSyncWorker.java`, `PosterRefreshWorker.java`, `DvbEpgRefreshWorker.java`, `OnlineEpgGuide.java`, `EpgShareFeedDirectory.java`, `StreamingXmlTv.java`, `BroadcastEpgProvider.java`, `BetterPostersProvider.java`, `UnifiedArtworkPrefetch.java`, `LocalInfoBarController.java`, `SatelliteGuideActivity.java`.

