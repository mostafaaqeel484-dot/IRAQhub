# IRAQhub X5M — إصلاح بدء المحرك المستقل MPV

## الخطأ المصوّر في تجربة المستخدم
`MPV_START_NoClassDefFoundError` يظهر مع شاشة سوداء بعد فتح محرك `IRAQhub 422 Engine`. هذا **ليس** FPS صفراً، ولا يثبت ضعف CPU في فك 4:2:2: فشل تحميل كلاس أو تهيئة مكتبة حدث قبل بدء الفيديو.

## ما عدّلناه
- نقل الاعتماد من `io.github.makd:libmpv:1.1.0` (نسخة Vulkan جديدة) إلى `dev.jdtech.mpv:libmpv:1.0.0` المستقرة ذات API `dev.jdtech.mpv.MPVLib` نفسه، كاختبار توافق لروم X5M.
- شيفرة `MPVLib` المصدرية تستدعي `System.loadLibrary("mpv")` ثم `System.loadLibrary("player")`. تسبقها IRAQhub الآن بتحميل صريح، حتى تظهر أخطاء `dlopen` مباشرة بدل اختفاء السبب عند إعادة تهيئة الكلاس.
- عند الفشل تعرض الشاشة اسم الكلاس المفقود أو `lib*.so` المفقودة مع سلسلة `Caused by`، ويكتب `android.util.Log.e("IRAQhub422", ... , t)` السجل الكامل.
- شاشة **Stats** تعرض الخطأ الأصلي، ABI، ونسخة MPV، حتى لو فشل التشغيل.
- بناء CI يرفض توزيع APK إلا إذا احتوى `libmpv.so` و`libplayer.so` على `arm64-v8a` و`armeabi-v7a` مع كلاس `dev.jdtech.mpv.MPVLib` في DEX. يختبر JUnit سلسلة أخطاء Java.

## اختبار على الرسيفر
1. ثبّت نسخة Debug التجريبية مع مراعاة توقيع النسخة الأصلية واحتفظ بنسخة احتياطية.
2. اختر نفس قناة WWE، `IRAQhub 422 Engine`، وخيار **Video only**.
3. إذا اشتغل MPV، اختبر حركة الصورة لمدة 30 ثانية، ثم عشر دقائق. **FILE_LOADED لا يثبت صورة متحركة**.
4. إذا تكرر السواد، صوّر السطر العلوي، واضغط **Stats** لنسخ نص `Bootstrap error`.
5. لأن ADB عبر `127.0.0.1:5556` مخوّل سابقاً، من جهاز عليه ADB مصادق عبر USB أو الشبكة يمكن تسجيل سبب الخطأ الحقيقي:
```sh
adb connect <receiver-ip>:5556
adb logcat -c
# أعد فتح محرك MPV من IRAQhub، ثم:
adb logcat -d -v time -s IRAQhub422:E AndroidRuntime:E linker:E
```
استبدل `<receiver-ip>` بعنوان الرسيفر الحقيقي (أو استخدم USB ADB الموصل). هذا الأمر للتشخيص فقط، لا يطلب root ولا يوقف خدمات النظام.

## حدود الإثبات
- نجاح JUnit/Android build واكتمال AAR بالـAPK يثبت وجود المكتبات داخل APK، لكنه **لا يؤكد** أن Firmware يتوافق مع JNI/GPU وأن MPEG-TS يدفع إطارات متحركة.
- إذا ظهر `java.lang.NoClassDefFoundError: Could not initialize class` بعد فشل مكتبة أصلية سابق، إعادة تشغيل العملية المنفصلة لازمة، إذ يبقى كلاس Java فاشل التهيئة بنفس العملية.
- لا تمس هذه التعديلات OEM / DVB / BISS / tuner / USB debugging أو صلاحيات النظام؛ LibVLC يبقى خياراً يدوياً.
