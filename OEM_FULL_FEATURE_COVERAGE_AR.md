# IRAQhub × Aurum — تنفيذ فهرس الوظائف المتبقية وحدود الصلاحيات
**2026-11-06 | فرع feature/aurum-dvb-integration-20261106**

## النتيجة المنفذة في هذه الجولة
- أضفنا **`OemFullSettingsActivity`**: شاشة إعدادات كاملة في تصميم IRAQhub نفسه تحتوي **58 وظيفة مصنفة في 7 مجموعات**، تظهر من **إعدادات IRAQhub** و **أدوات الرسيفر** وواجهة التكامل السابقة.
- أضفنا **`OemFeatureMenuIndex`**: خريطة مركّزة تربط اسم الوظيفة بالـOEM navigation graph الذي عُثر فيه على Fragment، وبأقرب **قائمة أصلية منشورة عبر Android exported activity**. يميّز كل مسار بين «موجود في موارد Aurum» و«محتمل وغير مؤكّد» ويطلب تأكيداً قبل فتح وظيفة تغيّر بيانات الرسيفر.
- أضفنا **`ChmaxControlCenterActivity`**: مركز CHMAX يتيح تفعيل مزامنة EPG والشعارات المصرّح بها عند تغيير القناة، تفعيل إضافة الشعارات الاختيارية لقائمة CHL، فتح إعدادات هوية المصنع، قراءة حالة المزامنة، وإعادة طلب تحديث مصادر EPG/البوسترات. يفتح قائمة القنوات الأصلية للحفظ/الاستعادة السحابية **ولا يدّعي التحكم بقاعدة Aurum**.
- أضفنا **`OemFeatureMenuIndexTest`** للتحقق من وجود مجموعات البحث والأقمار والقنوات وCHMAX والتسجيل، وأن جميع المسارات محصورة في شاشات محلية أو قوائم منشورة، وأن Blind Scan وUnicable معلّمان «غير مؤكدين».
- أضفنا قراءة **قوائم المصنع المنشورة ديناميكياً** من `dvb_host_main_menu` على جهاز المستخدم؛ لا تُفتح إلا الواجهات المصدّرة مع فحص الصلاحيات والتأكيد.
- أضفنا **`ChannelNumberEntry`** للتبديل المباشر بواسطة أزرار 0–9 وNumpad في شاشة DVB: اضغط OK للتأكيد أو اترك 1.5 ثانية. الأرقام تشير إلى فهرس قنوات IRAQhub، وليس قاعدة النظام. أضفنا اختبارات وحدة للحالات الحدية وربط أزرار الأرقام.
- أبقينا **واجهة HOME واسم حزمة IRAQhub ومشغل DVB الحالي** دون استبدال، مع بقاء Aurum مثبتاً.

## الأدلة من APK Aurum 1.0 (فحص ثابت وليس تشغيل الجهاز)
| المجموعة | إثبات وجود الوظيفة في الروم | مسار IRAQhub الجديد |
|---|---|---|
| Fast Scan | `nav_satellite` → `SatelliteFastScanFragment` | قائمة Installation الأصلية ثم اختيار البحث |
| Manual Search | `SatelliteManualFragment` | Installation ثم Manual |
| Blind / Network / Multi-Satellite | لم تُثبت صفحة مستقلة لكل خيار | Installation؛ يوضح أنها خيارات محتملة فقط |
| TP/Satellite/PID | `SatelliteFragment`, `EditPidFragment` | Installation؛ عمليات النظام داخل Aurum |
| DiSEqC Motor / USALS | `SatelliteMotorFragment`, `SatelliteUsalsFragment` | Installation ثم Motor/USALS |
| Unicable | لا دليل كافياً من الموارد | يظهر **غير مؤكّد** ولا يرسل أي أوامر |
| Satellite Finder | `SatelliteFinderFragment` | Installation |
| TKGS | `SatelliteTKGSFragment` | Installation |
| DVB-T / DVB-C | `nav_terrestrial`, `nav_cable` مع Fragments يدوية | Installation إذا كان التونر متاحاً |
| تعديل/نقل/حذف/فرز القنوات | `nav_channel`: `EditChannelFragment`, `DeleteChannelFragment`, `SortChannelFragment` | قائمة Channel الأصلية |
| مجموعات مفضلة/ألوان/قناة بدء التشغيل | `EditFavFragment`, `ChannelColorFragment`, `StartChannelFragment` | قائمة Channel |
| USB Backup/Restore | `UserChannelListFragment`, `UserLoadFragment` | قائمة Channel |
| CHMAX cloud list | `ExtUserChannelListPresenter.saveToCloud/loadFromCloud` | قائمة Channel، ليست API مستقلة |
| CHMAX auto-update | `ChmaxSettingPresenter.setChmChannelListAutoUpdate` | قائمة OEM Other مع توضيح أن صفحة الإعداد قد لا تكون ظاهرة |
| PVR Library | `com.excellence.osd.pvrpro.view.PvrProActivity` **غير مصدَّر** | يعرض تفسيراً ويتيح فتح التلفاز الأصلي، لا يفتح النشاط الخاص |
| TimeShift / REC | `RecordInfoFragmentPresenter.playTimeShift` داخل Aurum | فتح TV الأصلي واستخدام الريموت، لا تحكم مباشر من IRAQhub |
| مؤقت البرامج | `EventTimerActivity` exported | يفتح مؤقت OEM المنشور |
| EPG Pro / DVB TV | أنشطة OEM مصدّرة | عبر `OemDvbFeatureGateway` |
| Audio/teletext/CA/BISS/USB/network/firmware | كود وواجهات المصنع | تصفح قوائم OEM وفتح التطبيقات المصَدّرة فقط |

**مهم:** الدليل على Fragment داخل `nav_satellite` لا يعني إمكان تشغيله مباشرة من تطبيق آخر. المتاح فعلياً هو نشاط `com.tosmart.dvb.common.NavHostActivity` المصدَّر، الذي يفتح مجموعة Installation/Channel، ثم يختار المستخدم الشاشة الداخلية بنفسه.

## ما الذي لم نتمكن من نقله كوظائف مستقلة؟
1. **بحث تلقائي مستقل من IRAQhub** (Fast/Blind/Network/Multi-Satellite): لا يوجد API tuner scan معتمد وصالح لتطبيق غير نظامي. زر Installation لا يبدأ البحث تلقائياً.
2. **USALS/Motor وكتابة قاعدة ترددات وقنوات النظام مباشرة:** تظل في Aurum لحماية إعدادات التونر والقنوات. ملصقات LNB المحلية الموجودة في IRAQhub لا تعدّل جدول الشركة.
3. **إدارة القنوات الأصلية مباشرة خارج Aurum:** العمليات الأصلية تتم داخل واجهة OEM. القوائم المستوردة CHL داخل IRAQhub منفصلة عن قاعدة القنوات المحمية.
4. **تحميل/رفع قوائم CHMAX السحابية أو تفعيل Auto-Update في روم المصنع من IRAQhub:** في APK دوال خاصة فقط، ولم تثبت لها صلاحية أو API مصدّرة. مركز CHMAX يعالج المزامنة الآمنة للـEPG/الشعارات ويعرض الحالة.
5. **PVR / TimeShift المباشر:** لا نشاط PVR مُصدّر ولا واجهة موثقة للـPVR من تطبيق مستقل؛ مؤقت OEM يمكن فتحه، وتسجيل البث يعتمد على الريموت/TV الأصلي.
6. **مطابقة ريموت وInfobar حرفياً على العتاد:** أضيف سابقاً اختبار KeyCode/ScanCode وشاشة Infobar مع خيار OSD وعدم تداخل الشريطين. لا توجد اختبارات فعلية على طراز الرسيفر أو لقطات شاشة مزدوجة موثقة في هذه الجلسة.
7. **الوظائف الخاصة بتوقيع النظام/SELinux** لا يمكن اكتسابها بنسخ DEX/SO أو بتعديل `AndroidManifest.xml`. يجب SDK OEM وتوقيع وروم رسمي في الحالات التي تتطلب ذلك.

## فحص البناء مقابل اختبار الجهاز
- GitHub Actions يشغّل اختبارات الوحدة و`assembleDebug` و`assembleRelease`.
- النجاح في CI **لا يثبت** ضبط الإشارة أو عمل البحث أو حركة الموتور أو فك قناة 4:2:2 أو تسجيل USB أو اتصال CHMAX على الجهاز الفعلي.
- السورس الموجود على GitHub كان يحمل `versionName=3.6.5`، وAPK المستخدم للمقارنة اسمه `IRAQhub 3.7.0`. يجب توحيد السورس والتوقيع قبل تثبيت APK على الرسيفر.
- لا تحذف Aurum أو تستبدله بتطبيق بدون توقيع المصنع.

## الخطوة اللازمة للتكامل العميق الحقيقي
إذا توفر **SDK/DVB AIDL رسمي مع صلاحياته** أو **روم رسمي قابل للبناء بتوقيع OEM**، يمكن تنفيذ أوامر التونر وقاعدة القنوات والتسجيل وربطها بمنطق IRAQhub. بدونه، أقصى ربط موثوق هو استخدام الأنشطة الأصلية المصدّرة، ومزامنة البيانات المتاحة بإذن المستخدم.
