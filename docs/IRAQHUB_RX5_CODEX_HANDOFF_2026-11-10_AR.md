# IRAQhub RX5 4.0.4 — تسليم تغييرات السورس إلى Codex

> تقرير تاريخ 2026-11-10 عن تغييرات مؤكدة في GitHub. لم تُختبر على جهاز X5M فعلياً. لا تخلط بين نجاح CI وبين نجاح ربط مصنع CHMAX.

## المصدر

- Repository: `mostafaaqeel484-dot/IRAQhub`.
- Branch: `build/rx5-chmax-bulk-apk-20261110`.
- رأس التغييرات المراجع: `9249f7a25f82dd9735a3e38fcbf637ae1803d2b2` (قبل إضافة ملف التوثيق هذا).
- CI: https://github.com/mostafaaqeel484-dot/IRAQhub/actions/runs/42826672410 — **success**، اختبارات وحدة + Debug APK + فحص توقيع.
- Draft APK: https://github.com/mostafaaqeel484-dot/IRAQhub/releases/tag/untagged-3b8352cd2ea0d50deb92 ، الملف `IRAQhub_RX5_Sports_TheSportsDB_Debug.apk`، حجمه 92,860,601 بايت.
- `app/build.gradle`: الاسم البرمجي `4.0.4-alpha1`، versionCode `44`، معمارية `armeabi-v7a`.

## الملفات التي تغيرت

| الملف | التعديل |
|---|---|
| `app/src/main/java/com/smarthub/plus/FactoryRetryPolicy.java` (جديد) | فحص 5000ms؛ نجاح 60000ms؛ خطأ مؤقت 5000ms؛ فشل مصادقة/رفض خدمة/429 يعود بفاصل 300000ms. |
| `app/src/test/java/com/smarthub/plus/FactoryRetryPolicyTest.java` (جديد) | اختبارات إعادة الاتصال وأخطاء المصادقة وEPG غير المنشور. |
| `app/src/main/java/com/smarthub/plus/FactoryProvider.java` | حماية من HTTP المتزامن، عمل على IO، تتبع وقت المحاولة المقبلة، فصل حيوية logo cimg عن مطابقة NOW/NEXT، الحفاظ على EPG المحلي غير المتطابق، وعدم تحديث شعار لم يتغير. |
| `app/src/main/java/com/smarthub/plus/LocalInfoBarController.java` | فحص المصنع كل 5 ثوانٍ في جلسة التشغيل حتى لو اختفى شريط InfoBar، طلب صور القنوات من المسار الحصري. |
| `app/src/main/java/com/smarthub/plus/LogoRepository.kt` | تشغيل `chmax_exclusive_logos=true` افتراضياً، وعزل cache بواسطة `chmax:verified:`، وإيقاف الصور العامة / provider logo في المسارات الأساسية. |
| `app/src/main/java/com/smarthub/plus/ChlLogoAddon.kt` | ضبط تكرار محاولة CHMAX icon بعد رفض الخدمة أو غياب profile، وتطابق هوية القناة الفيزيائية. |
| `app/src/main/java/com/smarthub/plus/ArtworkDiagnosticsActivity.java` | حالة المصنع/EPG/المحاولة المقبلة ووضع CHMAX-exclusive بدون عرض أسرار الجهاز. |
| `app/src/main/java/com/smarthub/plus/TheSportsDBProvider.java` | aliases و `strTeamAlternate` ودعم `C.Palace` بوصفه `Crystal Palace`. |
| `app/src/main/java/com/smarthub/plus/SportsProgrammeArtwork.java` | إنشاء بطاقة مباراة من اسمَي الفريقين وشعاريهما مع تنبيه بأنها غير مؤكدة إن لم تتوفر مباراة موثقة. |
| `app/src/main/java/com/smarthub/plus/EpgArtwork.java` | عرض بطاقة الرياضة الاحتياطية ضمن مسار صور EPG الخاص بالـInfoBar. |
| `.github/workflows/android.yml` | تحديث JDK 21 / Gradle 8.11.1 وتصحيح رفع APK إلى Draft Release. |

## السلوك المقصود والحدود

1. **الـ5 ثوانٍ:** heartbeat للتحقق أثناء جلسة التشغيل فقط، وليست عملية تسجيل دخول أو تنزيل EPG كل 5 ثوانٍ. طلبات الشبكة الحقيقية محدودة بالـbackoff؛ لا خدمة خلفية دائمة.
2. **المصنع:** `FactoryClient` يستخدم TLS وجلسة صالحة؛ لا يوجد تجاوز لرفض factory auth أو غياب `chmaxProfile`. حالات مثل `FACTORY_CHMAX_PROFILE_REQUIRED` و`FACTORY_SERVICE_REJECTED_50000` قد تمنع الشعارات.
3. **4:2:2:** يُؤجل HTTP المكلف أثناء أولوية تشغيل الفيديو؛ لا يوجد ادعاء بإصلاح مشكلة تجمد إطارات 4:2:2.
4. **EPG:** تُقبل بيانات NOW/NEXT منفصلة إذا تطابق اسم البرنامج والتوقيت (فرق بداية/نهاية لا يزيد على 120 ثانية إن توفرت القيم المحلية). لا تطغى بيانات مصنع غير متطابقة على EPG الحالي.
5. **شعارات القنوات:** CHMAX-only في مسارات `LogoRepository` الأساسية؛ إذا غاب ترخيص المصدر أو الهوية الفعلية تُترك الصورة فارغة. يلزم تدقيق جميع الشاشات القديمة للتأكد من عدم وجود تجاوزات.
6. **البوسترات:** صور أندية TheSportsDB هي جزء من **بوستر البرنامج الرياضي**، وليست شعار قناة فضائية؛ لا تتعارض مع شرط CHMAX-only لشعارات القنوات.
7. **CHMAX Bulk:** العامل الذي ينزّل الشعارات بالجملة موجود في سورس ZIP معدل منفصل ولم يُدمج بالكامل في GitHub. لا تدّعِ غير ذلك.
8. **CI:** التوقيع Debug قابل للتجربة ولكنه قد يختلف عن توقيع نسخة المستخدم المثبتة. لا تقترح إزالة APK قبل أخذ نسخة احتياطية.

## المطلوب من Codex بعد قراءة هذا الملف

1. راجع Diff كل الملفات أعلاه مع التركيز على `FactoryProvider` و`LogoRepository` و`LocalInfoBarController`، وتحقق من أي race/cache invalidation أو اختلاف هوية channel بين قائمة القنوات والـInfoBar.
2. راجع `ChlLogoAddon.iconRetryAt`؛ هو throttle عام وقد يؤخر قناة أخرى بعد فشل إحداها. إن لزم افصل per-service cooldown عن global auth/rate-limit cooldown.
3. تأكد أن أي شعار من مسارات UI الأخرى لا يتجاوز CHMAX-only، ولا تُدخل تخمينات بناءً على الاسم.
4. تحقّق من NOW/NEXT والتوقيت العراقي وانتقال يوم EPG وسلامة حفظ `SatelliteState` أمام تحديث أحدث.
5. إن كان المطلوب تنزيل CHMAX logos بالجملة، ابدأ بدمج التعديل المنفصل على نحو مدقق وبصلاحيات رسمية وبإمكانية resume/cache، ثم اختبره على X5M.
6. أضف اختبارات unit/integration للتمييز بين logo/EPG/Poster ومطابقة الأسماء `C.Palace`، وشغل GitHub Actions من جديد.
7. اختبر ADB/logcat على الرسيفر الحقيقي ولا تعلن نجاح الاتصال CHMAX أو 4:2:2 أو DVB scan دون دليل من الجهاز.

## رسالة قصيرة يمكن نسخها إلى Codex

> اقرأ `docs/IRAQHUB_RX5_CODEX_HANDOFF_2026-11-10_AR.md` على فرع `build/rx5-chmax-bulk-apk-20261110`. افحص وطور إصلاحات CHMAX-exclusive logos، وإعادة التحقق بالـforeground كل 5 ثوانٍ مع auth backoff، ودمج EPG NOW/NEXT، وبوسترات TheSportsDB في InfoBar. ميّز بين نجاح CI والاختبار الفعلي، وأكمل CHMAX Bulk عند توفّر سورسه. احفظ قيود Android 14 ARMv7 وعدم تجاوز صلاحيات المصنع.
