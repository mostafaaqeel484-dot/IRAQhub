# IRAQhub — تحسينات 4:2:2 وإدخال BISS من Aurum
تاريخ التعديل: 2026-11-06.

## 1. تحسينات التوافق 4:2:2 (منفذة بالكود)
- توسيع مسح القدرات المُعلنة في `MediaCodecList` لتشمل MPEG-2 Profile 422 وAVC High422 وHEVC 422 إذا أعلن المصنع ملف profile صريحاً. إعلان الـprofile لا يثبت نجاح فك الفيديو على الرسيفر.
- تصحيح خطأ مهم في كشف فيديو HEVC: `hvc1.4...` هو **HEVC Range Extensions** وليس دليلاً كافياً على أن العينة 4:2:2. التصنيف صار «مرشح 4:2:2، chroma غير مؤكّد»، بينما `avc1.7A...` يُعرف بملف AVC High422.
- إبقاء Media3 `setEnableDecoderFallback(true)` الموجود مسبقاً. عند خطأ Decoder Init/Decoding مع بث يشتبه أنه 4:2:2، يُعرض خيار المستخدم لتجربة مشغّل خارجي مثبت على الجهاز. لا يحاول التطبيق تثبيت مفككات أو تعديل النظام.
- عند فشل مسار فيديو DVB الأصلي في IRAQhub، يعرض التطبيق فتح تطبيق التلفاز الأصلي الموقّع من الشركة كمحاولة توافق بديلة.
- تخفيف الضغط: `PlaybackLoadGuard` يؤجل مهام تنزيل/تحليل XMLTV وBetterPosters خلال عرض DVB أو بث IPTV 4:2:2/HEVC RExt أو ضغط ذاكرة على أجهزة قليلة الذاكرة. `IraqHubSyncWorker` يحتفظ بالبيانات القديمة ويطلب إعادة المحاولة من WorkManager. لا يوقف مشغل DVB ولا يغير التونر.
- جدولة التحديث 4 ساعات موجودة سابقاً في `IraqHubSyncScheduler` ولم تتغير. قد يتأخر التنفيذ عن 4 ساعات عند تشغيل فيديو حساس أو عدم توافر الشبكة؛ WorkManager ليس مؤقتاً دقيقاً.

## 2. BISS (الوظيفة الموثقة من Aurum)
من APK `com.tosmart.aurum` الأصلي ظهر:
- `com.tosmart.stb_extend.ext_view.popup.BisKeyEditPop`
- `com.tosmart.stb_extend.ext_presenterlib.other.IPtMenuContract$BISKey`
- `com.tosmart.stb_extend.ext_view.adapter.PatchMenuItemAdapter`
- `bis_key_edit_pop` و `STR_BIS = BISS`.

هذه الأدلة تثبت وجود محرّر BISS **داخل تطبيق الشركة** لكنها لا تثبت وجود API مصدّر لتمرير المفتاح من تطبيق آخر. لذلك أُضيف زر داخل IRAQhub يفتح `Patch Menu` الأصلية ويترك للمستخدم اختيار BISS وتحرير المفتاح في واجهة الشركة نفسها. لا نحفظ أو نطبع أو نرسل المفتاح من IRAQhub، ولا ننسخ أنشطة OEM الخاصة.

## 3. ملفات التعديل
- `VideoDecoderCompatibility.java`: تصنيف 4:2:2 دقيق، MPEG-2 422.
- `Media3PlayerActivity.java`: تشخيص HEVC RExt، حفظ حالة البث، خيار مشغل بديل.
- `DvbPlaybackActivity.java`: فتح تلفاز OEM عند فشل فك DVB.
- `PlaybackLoadGuard.java`: حماية الأداء.
- `IraqHubSyncWorker.java`: تأجيل المزامنة الثقيلة أثناء العرض الحساس.
- `OemDvbFeatureGateway.java`, `OemDvbIntegrationActivity.java`, `ReceiverToolsActivity.java`, `ChannelControls.java`: مسار BISS الأصلي.
- `PlaybackLoadGuardTest.java`, `VideoDecoderCompatibilityTest.java`, `OemDvbFeatureGatewayTest.java`: اختبارات وحدة.

## 4. نتائج مطلوبة على جهاز DVB
1. شغّل قناة MPEG-2 422، وAVC High422، وHEVC RExt إن توفرت، وسجل اسم decoder/أول إطار/إسقاط الإطارات/صوت الجهاز، قبل التعديل وبعده.
2. تحقق من نجاح التشغيل الأصلي أو الخارجي فعلياً. وجود profile وحده **ليس نجاحاً**.
3. خلال تشغيل DVB افتح EPG/بوستر من Cache، وتأكد أن البث مستمر. بعد ترك البث، افحص استئناف مزامنة 4 ساعات.
4. ادخل من **IRAQhub > أدوات الرسيفر > إدخال شفرة BISS**، ثم اختر BISS داخل Patch Menu التابعة للمصنّع. تأكد أن المحرر يفتح ويقبل الإدخال على **الجهاز نفسه**.
5. تأكد أن مفاتيح BISS لا تظهر في logcat أو SharedPreferences أو حزم المزامنة.
6. لا تحذف Aurum، ولا تعتبر APK التجريبي صالحاً للتثبيت فوق نسخة 3.7.0 قبل مطابقة السورس والتوقيع.

## 5. المواصفات الموحدة المرفقة
ملف مواصفات EPG والبوسترات يتطلب تقديم بث DVB على تحديثات الخلفية وعرض آخر بيانات صحيحة (الأقسام 2 و7 و11 و«حماية الأداء»). هذه التعديلات تنفذ **جزء حماية الأداء فقط** من المواصفة؛ لا تعتبر استكمالاً لكل مصادر EPG ومطابقة الصور وقواعد بيانات SQLite والاختبارات على الرسيفر، وتلك البنود تبقى مستقلة لحين إثبات التشغيل.

## حالة الاختبار
مكتمل بالكود، CI مطلوب، **اختبار جهاز DVB الحقيقي لم يُنفّذ في هذه البيئة**.
