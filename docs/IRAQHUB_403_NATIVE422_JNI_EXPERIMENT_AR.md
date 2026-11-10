# IRAQhub+ 4.0.3 Native422 JNI — حالة أولية / X5M

## التقني
- المحرك لا يستعمل libmpv أو LibVLC في مسار فك الفيديو: يبدأ من MPEG-TS
  صادر عن `LiveTsRelay` المصرح به على `127.0.0.1`، ثم
  `avformat_open_input`، `av_find_best_stream`،
  `avcodec_send_packet`، `avcodec_receive_frame`،
  `libswscale`، و `ANativeWindow_unlockAndPost`.
- مسار منفصل في Android: `:decoder422`. استخدام البرنامج يدوي
  فقط عبر إعداد المحرك، وبشرط وجود مكتبة JNI في APK التجريبي.
- يدعم خيارات 4:2:2 و4:2:0 بدقات خرج 1080/720/540/360/260،
  Auto 720→540→360→260. **الدقة لا تخفض عبء فك المصدر HEVC**.
- عند العرض النهائي يُرسل RGBA إلى Android Surface. صيغة 422
  تبقى داخل مسار المعالجة إلى التحويل الأخير، بينما صيغة 420
  تمر بتحويل 8-bit YUV420 قبل عرض RGBA.
- وضع GPU FAST هنا يعني عدم فرض التصغير ومعالجة Android Surface.
  **ليس** دليلاً على فك HEVC 4:2:2 بـGPU أو MediaCodec.
- النموذج الأولي فيديو فقط: لا صوت ولا ترجمة داخل JNI حالياً.
  يبقى MPV احتياطياً في حال الفشل أو توقف الإطارات.
- ذاكرة الإطارات مؤقتة ومحدودة بحجم عرض أقصى 1920×1080.
  لا يفتح ملفاً محلياً ولا رابطاً خارجياً في هذا المسار التجريبي.

## تثبيت واختبار
GitHub Actions يصنع APK تجريبياً فقط بعد نجاح توقيع مصدر FFmpeg
وتجميع ARMv7 وفحص `ELF32` و `apksigner`. لا يُنشر كإصدار رسمي.
الحزمة متميزة: `com.smarthub.plus.x5m403jni`.

```bash
adb connect 192.168.0.214:5556
adb install -r app-native422Preview.apk
adb -s 192.168.0.214:5556 logcat -c
# افتح إعدادات 422 واختر "تجربة FFmpeg JNI"؛ ثم قناة DVB
adb -s 192.168.0.214:5556 logcat -d -v time | grep -Ei 'IRAQhubNative422|IRAQhub422|libavcodec|ANativeWindow|hevc|TS_SOURCE'
```

## شروط القبول
- أول إطار معروض فعلياً، ثم ارتفاع `native422_rendered_frames`
  طوال عشر دقائق من بث DVB 4:2:2 HEVC.
- لا توقف عند أول إطار، ولا شاشة سوداء، ولا حلقة إعادة تشغيل.
- اختبار 422 و420 عند كل 1080/720/540/360/260، ثم GPU FAST.
- زمن تبديل القناة، إطارات فك/عرض، CPU/RAM والتزامن الصوتي
  (عندما يُضاف الصوت)، والرجوع الآمن إلى MPV.
- لا نعد 50fps أو دعم HW قبل الاختبار العتادي الحقيقي.
