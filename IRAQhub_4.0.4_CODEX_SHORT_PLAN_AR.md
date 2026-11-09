# IRAQhub+ 4.0.4 — خطة Codex المختصرة

**الهدف:** استخدم خدمات الرسيفر الأصلية المسموح بها خلف واجهة IRAQhub، ولا تعِد بناء وظائف تعمل بالفعل. الجهاز: **Amlogic X5M / Android 14 / ARMv7 (32-bit) / 4 GB**. المصدر: فرع `codex/iraqhub-404-handoff-20261109`، ومرجع PR #46.

## P0 — أولاً: اكتشاف نظام الرسيفر عبر USB Debugging

1. فعّل **USB debugging** من خيارات المطور، صِل الكمبيوتر عبر USB أو اتصال ADB سبق تفويضه، ثم وافق على نافذة **RSA key fingerprint** على شاشة الرسيفر بعد مطابقة البصمة مع جهازك. لا تنسخ مفتاح ADB الخاص إلى المشروع ولا تسجّل البصمات/المعرّفات الشخصية في التقرير.
2. شغّل هذه الأوامر لاكتشاف حزم المصنع وواجهاتها (استخدم `-s SERIAL` بعد `adb devices`):

```bash
adb devices -l
adb -s SERIAL shell getprop ro.build.version.release
adb -s SERIAL shell getprop ro.product.cpu.abilist
adb -s SERIAL shell pm list packages | grep -Ei 'tosmart|aurum|dvb|dtvkit|chmax|epg|droidlogic'
adb -s SERIAL shell dumpsys package com.tosmart.aurum > aurum_package.txt
adb -s SERIAL shell dumpsys package com.droidlogic.dtvkit.inputsource > dtvkit_package.txt
adb -s SERIAL shell dumpsys activity activities > activities.txt
adb -s SERIAL shell dumpsys tv_input > tv_input.txt
```

3. افحص **exported Activities / Services / Broadcasts / Providers** والصلاحيات ونتائج `SecurityException` من السجلات. جرّب فقط المكونات المصدّرة والمتاحة بإذنها؛ لا تفترض أن وصول ADB = صلاحية `system` أو صلاحية كتابة قاعدة CHMAX.
4. أنشئ جدول `OEM_BRIDGE_AUDIT.md`: **القسم | حزمة/مكوّن المصنع | طريقة استدعاء مصرح بها | يلزم توقيع مصنع؟ | حالة الاختبار**. لا تُظهر واجهة Aurum إذا أمكن بناء شاشة IRAQhub فوق Backend موثّق.

## P1 — ربط أقسام المصنع داخل IRAQhub

| القسم | المطلوب |
|---|---|
| **CHMAX** | اكتشاف خدمة وبيانات الشعارات، ربطها بهوية DVB ‏ONID/TSID/SID، استيراد/دمج CHL أو DB من ملف يختاره المستخدم، Cache سريع. لا تعتمد على SN إلا عند الضرورة الموثّقة. |
| **EPG / EPG Pro** | قراءة EIT/دليل المصنع إن كان مصرحاً، دمجه مع XMLTV دون تكرار، NOW/NEXT بسرعة، تحديث الخلفية، بقاء آخر بيانات سليمة. لا تكتفِ بفتح شاشة EPG Pro الأصلية. |
| **Installation** | شاشة IRAQhub للبحث Manual/Fast/Blind، إعداد TP والتونر والأنتينا؛ إصلاح `FIRMWARE_CAPTURE_NO_TS_DATA` و`NO_COMPLETE_PSI` وعدم إعلان قنوات قبل اكتمال PSI. |
| **Channels** | قوائم القنوات والمفضلة/القفل/الفرز/نقل القنوات مع حفظ حقيقي عند وجود API مخوّل، وUSB Backup/Restore. |
| **Settings** | LNB، DiSEqC، 22 kHz، USALS/Stop وUnicable عند دعم الخدمة؛ احفظ وتحقق من **تطبيق** الضبط الفعلي. |
| **Update** | اكتشف واجهة التحديث الرسمية USB/Network وحالتها؛ لا تبدأ تحديث Firmware أو تمسح البيانات تلقائياً. |
| **Other** | أدوات اللوحة الأمامية، الصوت/الترجمة، PVR، تشخيص الإشارة، اختبار الريموت. |
| **Clear Memory** | تمييز تنظيف Cache التطبيق عن Cache المصنع أو مسح بيانات القنوات؛ قدم إجراء آمناً بموافقة المستخدم، ولا تقتل عمليات DVB أو تحذف DB/الإعدادات. |

## P1 — تحسينات ضرورية متبقية

- **4:2:2:** عالج تجمد أول إطار/السواد مع استمرار الصوت؛ اختبر TS/HEVC وFFmpeg JNI والـSurface وسجّل `render_fps` الحقيقي. لا تعتبر نجاح APK إثبات تشغيل حي.
- **بوسترات الرياضة:** حلّل `Liverpool vs C.plas` إلى **Liverpool / Crystal Palace** باستخدام aliases معتمدة؛ عند غياب بوستر المباراة اعرض **شعار النادي الأول يساراً والثاني يميناً**. حتى إن غابت مباراة موثقة، اعرض بطاقة شعاري **فريقين مستنتجين من EPG** مع توضيح أن المباراة غير مؤكدة؛ لا تخترع نتيجة/بطولة.
- **InfoBar:** **ألغِ مربع الإشارة الذي يظهر أعلى الشاشة تلقائياً** عند فتح InfoBar أو تغيير القناة، وأزل تكرار مؤشرات الإشارة؛ أبقِ معلومات Signal/Quality/Bitrate مدمجة بشكل صغير داخل الشريط السفلي. إذا طلب المستخدم لوحة إشارة تفصيلية بالزر BLUE، افتح نافذة صغيرة مستقلة قابلة للإغلاق، لا مربعاً دائماً أعلى الشاشة. مدة الظهور قابلة للضبط **1–15 ثانية** فعلياً من `OsdSettingsActivity` و`LocalInfoBarController` (الحالي يقصها إلى 10). اجعل الشريط أسود خفيفاً بأسلوب RX/Tiger مع NOW/NEXT وملخص وNo Signal دون حجب الصورة.
- **ظهور البوسترات:** اعرض بوستر NOW وبوستر NEXT على **جانبي الشاشة أعلى InfoBar** بحجم متوازن، لا وسط الصورة؛ اجلب صورة الكاش فوراً ثم حدّثها بالخلفية دون وميض أو إعادة تعيينها عند تحديث EPG. طابق البرنامج بالعنوان/الوقت/القناة، وأظهر ملخصاً مختصراً، واستعمل بطاقة VS بشعارَي الفريقين عند غياب بوستر رياضي؛ عند غياب كل الصور استخدم بطاقة نصية أنيقة. لا تجعل جلب الصور يبطّئ فتح InfoBar أو تشغيل 4:2:2، وألغِ طلبات القناة القديمة عند التبديل.
- **BISS:** نافذة عائمة من PageDown، والبحث اليدوي مستقل؛ التحقق من الصيغة وربط OEM CA المسموح، دون تسجيل المفاتيح أو الادعاء بفك تشفير لم يثبت.

## التسليم — أقل استهلاك لرسائل Codex

**الترتيب:** `ADB + OEM audit → CHMAX/EPG/Installation/Channels → Manual Scan/Antenna → 4:2:2 → Sports/InfoBar/RX → BISS/Other`.

اعمل على PRات صغيرة. لا تكتب تقارير مطوّلة في كل خطوة؛ أخرج فقط **المشكلة / السبب المثبت / الملفات المعدلة / نتيجة الاختبار / المانع إن وجد**. اختبر Gradle وAPK النهائي الموقّع وتثبيته فعلياً عبر ADB، ولا تدمج إلى `main` قبل اختبارات X5M. احمِ بيانات المستخدم ومفاتيح النظام.