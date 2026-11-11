# X5M Android TV 14 · ARMv7 32-bit — معاينة آمنة

المصدر: PR #39؛ نفس محرك libmpv و422/420 متعدد الدقات، لكن كل المكتبات الأصلية `armeabi-v7a` فقط. جهاز أندرويد 32-bit لا يستخدم `arm64-v8a`. Android 14 هو API 34، والتطبيق يدعم minSdk 26 وtargetSdk 34.

### لماذا لا نعتبر هذه معالجة أكيدة للخطأ؟
رسالة المثبّت `App not installed as package appears to be invalid` عامة، وقد تحدث بسبب اختلاف التوقيع، تلف التنزيل، تحليل الحزمة، ملف غير مكتمل، أو قيود ROM. الإصدار السابق كان يتضمن `armeabi-v7a` بالفعل، ولذلك حذف arm64 خطوة تقليل/تبسيط وتشخيص، **وليس إثبات أن ABI هو السبب**.

اسم المعمارية الصحيح في Android NDK هو `armeabi-v7a` (ARMv7-A). `ARMv7-M` يخص فئة المعالجات المضمنة، ولا يطابق Android app ABI؛ نحتاج نتيجة getprop للتأكد من وصف جهاز المستخدم.

هذه النسخة تستخدم الحزمة المستقلة `com.smarthub.plus.x5marmv7` حتى تبقى نسخة IRAQhub المثبتة سليمة. لا تحذف النسخة الأصلية ولا تتوقع نقل بياناتها تلقائياً.

### إذا فشل التثبيت مرة أخرى
من جهاز لديه ADB مصرح للرسيفر وعنوان IP الصحيح/المنفذ المتاح (5555 أو 5556، حسب وضع ADB المفعّل):

```sh
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.product.cpu.abilist32
adb shell pm path com.smarthub.plus
adb install -r -t IRAQhub_4.0.0_X5M_Android14_ARMv7_Only_Preview.apk
```

انسخ سطر `Failure [INSTALL_FAILED_...]` كاملاً. فشل `INSTALL_FAILED_UPDATE_INCOMPATIBLE` يعني توقيع غير مطابق لنسخة preview مثبتة، وليس معمارية. فشل `INSTALL_FAILED_NO_MATCHING_ABIS` يعني معمارية. فشل `INSTALL_PARSE_FAILED_*` يعني تحليل/سلامة الحزمة. المساحة تظهر باسم `INSTALL_FAILED_INSUFFICIENT_STORAGE`. نفحص SHA256 وحجم الملف من تنزيلات الجهاز/الهاتف إذا لزم.

### CI
العمل `.github/workflows/x5m-android14-armv7-only-preview.yml` يبني `assembleX5mPreview`، ويفحص ABI الوحيد، DEX، الرموز المطلوبة لـ libmpv ضد NDK r29 libc++، ZIP CRC، SDK/targetSdk، ومعرف الحزمة والتوقيع والمحاذاة. نشر APK في Draft Release فقط عند النجاح، ولا يثبت القبول بواسطة ROM.
