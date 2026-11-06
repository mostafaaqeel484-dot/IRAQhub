# مقارنة تركيب Aurum ونقله إلى IRAQhub — مرحلة التركيب

**المصدر:** APK `com_2.tosmart.aurum_1.0.apk` (DEX + Manifest + موارد navigation)، وAPK IRAQhub 3.7.0 وسورس فرع `test-3.7.0-RX`. الملفان مجمّعان، وليسا شجرتي كود المصنع الأصليتين.

## مخطط القوائم الأصلي — دليل من Navigation XML

- `nav_satellite.xml`: `SatelliteFragment`، `SatelliteFastScanFragment`، `SatelliteAntennaFragment`، `SatelliteManualFragment`، `SatelliteMotorFragment`، `SatelliteUsalsFragment`، `EditPidFragment`، `SatelliteFinderFragment`، `SatelliteTKGSFragment`، `nav_search_result`.
- `nav_terrestrial.xml`: `TerrestrialFragment`، `TerrestrialManualFragment`، `nav_search_result`، `EditPidFragment`.
- `nav_cable.xml`: `CableFragment`، `CableManualFragment`، `nav_search_result`.
- `nav_channel.xml`: `EditChannelFragment`، `EditFavFragment`، `SortChannelFragment`، `UserChannelListFragment`، `DeleteChannelFragment`، `StartChannelFragment`، ومسارات الألوان.

## ما نُقل فعلاً داخل شاشات IRAQhub

| القسم | ملفات IRAQhub | تنفيذ الوظيفة |
|---|---|---|
| التركيب والقائمة | `ReceiverToolsActivity` | قوائم IRAQhub، لا تفتح Aurum |
| الأقمار والترددات | `InstallationEditorActivity`, `ReceiverCatalog` | إضافة قمر وتغيير اسمه؛ إضافة TP وحذف TP خالٍ من القنوات؛ التنقل بين صفحات الترددات |
| LNB وDiSEqC | `TunerSettingsActivity`, `LnbProfile` | ملف محلي يحوي LOF منخفض/مرتفع، 22 kHz، طاقة LNB، منافذ 1.0/1.1؛ يرسل إعدادات الضبط للتونر عند تشغيل قناة |
| موتور USALS | `MotorCenterActivity`, `UsalsLocation`, `DvbTuner` | إعداد موقع وقمر، تمكين اختياري، طلب حركة ضمن حزمة Tuner، STOP؛ **غير مُختبَر على العتاد** |
| البحث اليدوي | `ManualTransponderActivity`, `DvbTuner` | أمر `Tuner/0002` وتحقيق قفل الإشارة مرتين عن طريق `Tuner/0003` |
| فحص سريع للترددات المحفوظة | `TransponderSweepActivity` | يكرر الضبط وقياس S/Q حسب قائمة TPs، يحاول مطابقة TP الفعلي مع قائمة SIDs من `ReceiverDvbBridge`، يحفظ تقريراً ويتيح الإيقاف |
| العثور على القمر | `SignalInformationActivity` | واجهة قياس إشارة فعلية |
| إدارة قنوات IRAQhub | `ChannelManageActivity`, `ReceiverCatalog` | بحث وتعديل اسم، ترتيب، حذف، مفضلة، تشغيل، تعديل Video/Audio/PCR/PMT PID ضمن قاعدة التطبيق |
| DVB و4:2:2 وInfobar/EPG والبوسترات | شاشات التشغيل والـOSD الحالية | لم تُستبدل. الـInfobar داخل نافذة تشغيل IRAQhub؛ البوسترات والجدولة الحالية محفوظة |

## حدود فنية مثبتة — لا يجوز الادعاء بغيرها

1. محرك Aurum الأصلي يحتوي `com.dvb.search.SearchManager` و`SearchManager2` و`com.dvb.search.UpdateSearchCallback`، ومنها `blindSearch` و`dvbTpSearch` و`dvbStopBlindSearch`، لكن هذه **ليست API عامة مثبتة للتطبيق المستقل**. لا يوجد تنفيذ موثوق لبدء مسح كامل من IRAQhub وحفظ القنوات الجديدة مباشرة إلى قاعدة النظام.
2. `com.tosmart.dvb.common.provider.ProgramContentProvider` في Manifest الأصلي `exported=true` ويحمل `android:authorities="com.tosmart.dvb.common.program.provider"`، لكنه محمي بـ`com.tosmart.dvb.common.permission.READ_PROGRAM_INDEX`، وهي صلاحية `protectionLevel=0x2` (**signature**). لذلك لا يمكن اعتبار قاعدة المصنع متاحة لتطبيق IRAQhub العادي.
3. `TransponderSweepActivity` يعرض LOCK والـSIDs المنشورة في `EPG_TDT` عند المطابقة، لكنه **لا يستخرج اسم القناة والـPMT/PIDs والـCA والتخويل، ولا ينشئ قناة قابلة للتشغيل من SID فقط**. هذا ليس Blind Scan ولا Network Scan.
4. إدارة القنوات المذكورة تُعدّل قاعدة SQLite الخاصة بـIRAQhub وليس قاعدة قنوات Aurum. استيراد ملف CHL جديد قد يستبدل بعض تعديلات التسمية والترتيب؛ ينبغي أخذ نسخة احتياطية قبل استيراد جديد.
5. الميزات الأخرى غير المنقولة بعد: بحث Blind/Network/Full/Multi-Satellite الفعلي وحفظ القنوات الممسوحة، إعدادات Unicable المتقدمة، حد حركة موتور DiSEqC 1.2 والحركة الاتجاهية والخطوات/المواقع، TKGS، DVD-T/C، وTimeShift الكامل.
6. اكتمال البناء لا يُثبت نجاح محرك البحث أو حركة الموتور على الجهاز. اختبارها عبر ADB USB الفعلي مطلوب قبل استعمالها كوظائف مثبتة.

## موقف الـInfobar والبوسترات

لم يتغير تخطيط `NowNextView` والـHOME في هذه المرحلة. بقيت وظائف CHMAX/EPG والبوسترات والمزامنة التي كانت موجودة. شاشة DVB المدمجة تعرض الـInfobar من داخل نشاط التشغيل، وليس بالضرورة عبر `SYSTEM_ALERT_WINDOW`، والتحديث الثقيل يؤجَّل أثناء تشغيل 4:2:2 البرمجي وفق الآلية الموجودة.

## التالي منطقياً

لإتمام البحث الحقيقي ينبغي إثبات أمر Search IPC متاح أو الحصول على SDK/AIDL مخوّل، مع تجربة رد نتائج البحث (service id + PID + codec + TSID + ONID) على الرسيفر. عندها تربط نتائج البحث محلياً وبشكل اختياري بقاعدة المصنع **فقط** إذا توفرت صلاحياته الرسمية. إلى ذلك الحين لا نخترع القنوات ولا نستبدل واجهة حقيقية بواجهة شكلية.
