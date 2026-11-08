# Gazal Auto Time Shift — BEN10-style beta v0.2

هذا **مشروع مستقل** مستلهم من واجهة ووظائف BEN10 Tiger Auto Time Shift v1.0.7، ويعمل بحزمة `com.gazal.audiosync`. ليس نسخة مطابقة للمحرك الأصلي ولا يتضمن `core.b10` المحمي أو نظام ترخيص BEN10.

## المنفذ فعلياً
- شاشة تلفزيونية أفقية 3 ثيمات وشفافية/حجم، 6 مصادر بكل صفحة، أزرار الريموت الأحمر والأخضر والأصفر والأزرق.
- استيراد قوائم HTTP(S) M3U / M3U8 النصية، وCFG النصي البسيط، وإضافة روابط صوت يدوياً وحفظها.
- استخراج `tvg-id` من EXTINF وربطه بملف XMLTV أو XMLTV.gz محلي؛ عرض NOW/NEXT من بيانات صحيحة عند وجودها.
- مشغّل صوت خارجي عبر Android MediaPlayer، Play/Pause/Stop، إظهار حالة تشغيل وأخطاء الاتصال في الإشعار.
- تأخير بدء المصدر الخارجي بالمللي ثانية (0–30 ثانية داخل المشغّل، 0–120 ثانية في الواجهة).
- لوحة عائمة قابلة للضغط فوق Aurum بعد موافقة SYSTEM_ALERT_WINDOW، تعرض أول ستة مصادر ومفاتيح التحكم.
- فحص قراءة فقط لعقد demux6/dvr6 ومعلومات PID والمنفذ المحلي ADB.
- **DVB Session experimental:** شاشة تستخدم Android TV Input `TvView`، تعرض قنوات إذا سمحت قاعدة TvContract، وتطلب `timeShiftPause/timeShiftSeekTo/timeShiftResume` مع عرض timestamps التي يوفرها TV Input.

## لم يكتمل بعد — مهم
- ليس لدينا محرك مطابق لمكتبة BEN10 الأصلية لالتقاط TS وفك PCM والمقارنة بالبصمة الصوتية.
- المزامنة الآلية لصوت DVB مع الصوت الخارجي **غير منفذة**. لا توجد قراءة fake لمستوى `AUDIO VERIFIED` ولا `SYNCED`.
- تأخير فيديو DVB يعمل فقط إذا TV Input يعرّض قنوات ويدعم TimeShift رسميّاً. **لم يتم التحقق على Gazal الفعلي**. خدمة Aurum قد لا تسمح لتطبيقات طرف ثالث بجلسة tuner ثانية.
- كتم صوت DVB أثناء بقاء Aurum في المقدمة ليس منفذاً. إيقاف صوت التطبيق الخارجي لا يكتم صوت القناة.
- ملفات CFG المشفرة الخاصة بالمزوّد غير مدعومة. بث DRM أو HLS يتطلب headers/كوكيز قد لا يعمل بـ MediaPlayer.
- تأخير بدء الصوت ليس حفظاً مستمراً لـPCM في buffer، ولا يتتبع تغيير تأخير البث تلقائياً.

## تجربة Gazal
جهاز الهدف Android 14 / armeabi-v7a / Amlogic X5M؛ مسارات `/dev/dvb0.demux6` و`/dev/dvb0.dvr6` ظهرت في فحص ADB سابق. هذا لا يعني أن القراءة أو TimeShift نجحا على الجهاز.

ثبت APK من `dist/Gazal_Audio_Sync_v0.2_debug.apk` (debug signed) ثم:
```bash
adb -s 192.168.0.214:5556 install -r Gazal_Audio_Sync_v0.2_debug.apk
adb -s 192.168.0.214:5556 shell am start -n com.gazal.audiosync/.Ben10ConsoleActivity
```
عند تعطل التطبيق:
```bash
adb -s 192.168.0.214:5556 logcat -d -b crash -v time | tail -n 90
```

## تجربة وظيفة DVB
افتح `DVB Session` من الشاشة. إذا القنوات غير متاحة، راجع رسالة الصلاحية بدل عمل spoof. إذا بدأت جلسة TvView فعلاً، جرّب pause/seek/resume على قناة مفتوحة وتحقق من تأخر الصورة الفعلي.

## البناء
`gradle assembleDebug` باستعمال JDK 17 و Android SDK 35 وGradle 8.10.2.
أرشيف Android APK ينتج من `gazal-audio-sync/app/build/outputs/apk/debug/app-debug.apk`.

**ممنوع الادعاء بأن هذا التطبيق يطابق BEN10 بنسبة 100% قبل اجتياز اختبارات Gazal للصوت والتأخير والمزامنة.**
