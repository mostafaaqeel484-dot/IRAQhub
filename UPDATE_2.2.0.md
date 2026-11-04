# IRAQhub 2.2.0 — Receiver integration

Android TV / Google TV, target API 34; ARMv7 and ARM64. Package `com.smarthub.plus` is unchanged.

The new factory provider connects directly from the receiver over verified HTTPS. It discovers the manufacturer's public service address, authenticates with this receiver's normally readable hardware identity, and stores its own access/refresh session in an Android Keystore AES-GCM vault. It does not copy the launcher's token, private database, signature or system UID. User provider credentials remain in their existing separate vault. The optional setup page allows the owner to supply their receiver identity/account if Android does not publish the required properties.

Online EPG runs off the rendering thread. Channel identity, programme title and time are checked before a response can replace current/next artwork. Responses for a channel that has changed are discarded. Requests are limited to once per minute per programme context, with a longer backoff for authentication/server failures. The EPG field `subtitle` is episode metadata, never a spoken subtitle track. No programme fixture is displayed as live data.

The OSD defaults to 26% of screen height (281px on this 1080p receiver), up from 189px. The real receiver INFO container was subsequently observed at 316 per mille; the on-device layout measured 341px and kept the channel-logo tile on the left. The vertical position also follows the exposed receiver container after observation. A receiver INFO container exposed through accessibility can supply its actual height; otherwise the larger default remains. Manual size 16–40% is available in OSD settings. The channel logo remains on the left, with a separate provider tile and larger current/next poster areas. The 10-second display timeout remains.

Subtitle integration observes a selected native subtitle row when the receiver exposes it, and uses its language as the translation source hint unless manually overridden. Visible native text and Android-authorized bitmap OCR remain separate input paths. ReceiverDvbBridge now makes a verified read-only native EPG request on the receiver loopback transport. Both the acknowledgement and the later SID-list event are required. The real reply contains native transponder identifiers and colon-separated service IDs; the reader validates and counts them off the UI thread once per minute. This is a real DVB service discovery path, not a raw subtitle decoder. Binding a SID to a named current channel still requires actual published native metadata; a channel display number is never substituted for a native programme ID. No renderer registration, module initialization, retuning or native callback takeover is performed. Subtitle font size and timing controls remain available.

See the release testing report for the actual receiver/API results and remaining limitations.

The factory discovery response names separate data and EPG origins. A redirect may carry the independently acquired factory session only to a published HTTPS origin, on the unchanged EPG route and query, with at most two redirects. Authentication requests never follow redirects. Device verification obtained a valid encrypted session, but the live schedule search returned service code 50000 (Server error). This release reports that failure and continues reading exposed native/XMLTV EPG. It does not claim that factory poster downloads or full CHMAX raw integration succeeded.


# IRAQhub 2.2.0 — نتائج التنفيذ والاختبار

APK مستقل لحزمة com.smarthub.plus، Android TV / Google TV، target API 34، ARMv7 وARM64. الاتصال من التلفزيون مباشرة دون Backend على الكمبيوتر. احتُفظ بملف إعداد المصادر المشفّر وبيانات المستخدم.

## ما نُفّذ

- طبقة FactoryClient / FactoryProvider لمصادقة المصنع واكتشاف خادمه وطلبات EPG والصور. هوية الجهاز الفعلية تُقرأ بإذن Android المتاح، وتُحفظ جلسة التطبيق الخاصة بـAES-GCM وAndroid Keystore. لا توكن من اللانشر ولا توقيع مصنع منسوخ.
- معالجة تحويل EPG إلى عنوان آخر ينشره اكتشاف المصنع، عبر HTTPS مع التحقق من المسار والاستعلام والوجهة. لا تُتبع تحويلات المصادقة ولا يُرسل التوكن إلى عنوان خارجي غير منشور.
- طبقة ReceiverDvbBridge للقراءة المحلية الفعلية: تطلب قائمة خدمات EPG، وتنتظر الرد اللاحق 0018 بعد تأكيد 0006، في مسار خلفي كل دقيقة. لا تعيد تهيئة وحدة DVB ولا تغيّر القناة أو عارض الترجمة الأصلي.
- مطابقة القناة وبرنامجها وتوقيته قبل نشر بيانات مصنع. ربط الإنترنت بالقمر والتردد وSID الفعلي، مع رفض استخدام رقم ترتيب القناة كمعرّف DVB.
- تكبير شريط OSD تلقائياً من أبعاد حاوية INFO الحقيقية، مع خيار يدوي 16–40%. قيس الشريط على الجهاز بـ341px، والشعار ثابت في اليسار. يتبع موضع الشريط الحاوية الأصلية عند نشرها. مدة العرض عشر ثوانٍ.
- قراءة لغة مسار الترجمة المحدد حين ينشره اللانشر، ثم استخدامها للترجمة النصية / OCR. أزرار A− وA+ وتوقيت SRT/ASS تبقى متاحة.

## الاختبارات الفعلية

- اختبارات الوحدات: 139، إخفاقات وأخطاء: 0.
- مصادقة المصنع من UID التطبيق: ناجحة. التوكن وهوية الجهاز لا يظهران كنص في ملف الجلسة المشفّر.
- قارئ DVB الإنتاجي: اتصال وقراءة ناجحان، عدد سجلات الترددات الأصلية 9. اكتمال تنسيق SID: false. تحتوي البيانات على حقل SID مركّب لا يطابق التنسيق المقروء؛ يُعرض عدم توافر التنسيق ولا تُخمن المعرّفات أو أعداد الخدمات.
- شعار MBC3: مطابقة فعلية MBC3.ae، HTTP 200، 512x507، وفك الصورة إلى Bitmap: true.
- اختُبر تكبير النص إلى 40sp و64sp والتحكم بالريموت وإبقاء النص خارج منطقة OCR. واختُبر نموذج OCR والترجمة إلى العربية بجملة اختبار معلنة. لم يُعتبر ذلك فكاً لمسار DVB/TXT خام أو اختباراً حياً جديداً لقناة أجنبية.
- التوقيع: APK Signature Scheme v2. فُحص عدم تضمين المفاتيح المعروفة والملفات الخاصة وحزمة التزويد الاختبارية في APK.

## ما بقي غير متاح

استعلام EPG على خدمة المصنع أعاد FACTORY_SERVICE_REJECTED_50000. نجحت المصادقة، لكن جلب جدول وصور CHMAX مباشرة لم ينجح في هذا الاختبار. تبقى معلومات EPG التي يعرضها اللانشر وXMLTV وصور TMDB المتطابقة هي المصادر العاملة، ولا تُعرض نتائج تجريبية كبيانات مصنع.

قائمة SID وحدها لا تكفي لربط كل برنامج بالقناة الحالية. تنسيق SID المختلف وغياب معرّف البرنامج الأصلي المنشور لا يُعالجان باختراع أرقام. كذلك لم يُنشر مسار جمل DVB Bitmap أو Teletext خام للتطبيق المستقل. تعمل ترجمة TXT الظاهرة أو OCR المسموح من Android؛ توقيتها وجودتها تقريبيان. لم يُثبت في هذا الإصدار فك خام أو توافق تلقائي كامل مع الصوت.

SHA-256: f581521b61679e00deeeb8b06d83b0d1d5ac3e61201ff8df69d4889280043698

تم التحقق من تطابق SHA-256 للنسخة المثبّتة مع APK المرفق، وبقاء ملف إعداد المصادر المشفّر. أُزيلت حزمة اختبارات الجهاز، وشُغّل المرافق فوق القناة الأصلية بلا خطأ خدمة.
