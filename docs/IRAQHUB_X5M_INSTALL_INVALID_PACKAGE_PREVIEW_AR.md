# تجربة تثبيت آمنة — IRAQhub X5M 4:2:2 Preview

صورة جهاز X5M أظهرت رسالة Android: `App not installed as package appears to be invalid` أثناء تثبيت APK مبني من PR #38.

## ما نعرفه
- بُني APK PR #38 بنجاح، واجتاز `apksigner verify` و`zipalign` وفحص DEX والرموز المطلوبة في MPV.
- نسخة Debug توقّع بمفتاح *مؤقت مختلف بين تشغيل GitHub Actions وآخر*. مع `applicationId=com.smarthub.plus` يمكن أن يرفض Android التحديث في حال اختلاف توقيع الحزمة المثبّتة. هذه فرضية قوية وليست تشخيص `pm install` مثبتاً بعد.
- قد يكون ملف الجهاز نُقل/نُزّل بشكل ناقص، أو الجهاز يعطي سبباً آخر. لا تثبت الصورة وحدها السبب الدقيق.
- **لا تحذف IRAQhub الأصلي**؛ في manifest `allowBackup=false`، وحذفه قد يمسح إعداداته وقنواته المحلية.

## APK تجريبي منفصل
- الاسم الظاهر: `IRAQhub 422 Preview`.
- الحزمة: `com.smarthub.plus.x5mpreview`، وليس `com.smarthub.plus`.
- التوقيع: CI Debug مؤقت، لا يحدّث الحزمة الأصلية.
- كل الملفات والتفضيلات وقاعدة البيانات تخص تطبيق المعاينة وحده. **لا نضمن ظهور القنوات والإعدادات الموجودة في IRAQhub الأصلي**، ولا أن خدمة DVB التابعة للـROM تسمح لتطبيق المعاينة بكل التكاملات قبل تجربة الجهاز.
- إذا احتجت حذف النسخة التجريبية لاحقاً، تأكد من اسم `Preview` ورقم الحزمة قبل الحذف، ولا تحذف التطبيق الأصلي.

## عند استمرار الفشل
افحص حجم وSHA256 للـAPK مقابل الملف المرفق في GitHub Draft Release. على هاتف Termux متصل بالرسيفر بعد سماح RSA:

```sh
pkg install android-tools -y
adb connect IP_OF_X5M:5556
adb shell pm path com.smarthub.plus
adb shell pm path com.smarthub.plus.x5mpreview
adb install -r ~/storage/downloads/IRAQhub_4.0.0_X5M_422_420_Install_Safe_Preview.apk
```

غيّر `IP_OF_X5M` إلى عنوان الرسيفر الفعلي؛ الأمر الأخير يتطلب وجود APK في تنزيلات **الهاتف** لا الرسيفر. لا تستخدم `adb uninstall com.smarthub.plus`. إذا ظهر `INSTALL_FAILED_UPDATE_INCOMPATIBLE` فهذا تعارض شهادة تحديث، أما `INSTALL_PARSE_FAILED` فيشير إلى تحليل الحزمة، و`INSTALL_FAILED_INSUFFICIENT_STORAGE` إلى المساحة، وهكذا. شارك النص الكامل للخطأ لتشخيصه.

## CI
`.github/workflows/x5m-side-by-side-install-preview.yml` يفحص معرف الحزمة منفصلاً، توافق MPV 32/64-bit، صحة Zip CRC، التوقيع، ورفع Draft خاص عند النجاح. الـCI لا يختبر مثبت أندرويد على جهاز X5M حقيقي ولا يثبت تشغيل 4:2:2.
