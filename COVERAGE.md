# IRAQhub — تقرير تغطية البيانات

فحص اتصال فعلي 2026-10-31 و2026-11-01. المعرفات التالية تحقق منها lookupleague.php وأعاد المصدر Soccer والاسم الصحيح، ولا تُستنتج أرقام بطولات. عدد مباريات الموسم في OpenLigaDB هو عدد السجلات التي أعادها API، وليس ضماناً لجودة أو اكتمال كل تفاصيلها.

| البطولة المطلوبة | TheSportsDB ID | اسم المصدر/توفر metadata والشعار | احتياطي OpenLigaDB الفعلي |
|---|---|---|---|
| Premier League | 4328 | English Premier League · HTTP 200 · badge نعم | pl / 2026 · 380 |
| La Liga | 4335 | Spanish La Liga · HTTP 200 · badge نعم | la1 / 2026 · 380 |
| Serie A | 4332 | Italian Serie A · HTTP 200 · badge نعم | غير متحقق في المصدر الاحتياطي |
| Bundesliga | 4331 | German Bundesliga · HTTP 200 · badge نعم | bl1 / 2026 · 306 |
| Ligue 1 | 4334 | French Ligue 1 · HTTP 200 · badge نعم | غير متحقق في المصدر الاحتياطي |
| UEFA Champions League | 4480 | UEFA Champions League · HTTP 200 · badge نعم | ucl / 2026 · 144 |
| UEFA Europa League | 4481 | UEFA Europa League · HTTP 200 · badge نعم | uel2026 / 2026 · 144 |
| UEFA Conference League | 5071 | UEFA Conference League · HTTP 200 · badge نعم | uecl2026 / 2026 · 108 |
| UEFA European Championship | 4502 | UEFA European Championships · HTTP 200 · badge نعم | غير متحقق في المصدر الاحتياطي |
| Iraqi Stars League | 5056 | Iraqi Premier League · HTTP 200 · badge نعم | غير متحقق في المصدر الاحتياطي |
| Iraqi FA Cup | غير موثق | لم يُعثر على معرّف موثق؛ لا بيانات مختلقة | غير متاح: لا ID موثق |
| FIFA World Cup | 4429 | FIFA World Cup · HTTP 200 · badge نعم | غير معتمد في احتياطي هذه النسخة |
| AFC Asian Cup | 4866 | AFC Asian Cup · HTTP 200 · badge نعم | غير متحقق في المصدر الاحتياطي |
| AFC Champions League Elite | 4719 | AFC Champions League Elite · HTTP 200 · badge نعم | غير متحقق في المصدر الاحتياطي |

TheSportsDB: المجاني يعيد حتى 3 مباريات/يوم لكل بطولة، ومباراة واحدة للقادمة والسابقة، و15 من الموسم. ليست تغطية Live مدفوعة. لهذا لا يُوصف الجدول بأنه كل مباريات العالم أو تغطية حية كاملة. التطبيق يعرض كل سجلات المصدر التي أتاحها في نافذة الطلب، دون حصر بأندية مختارة.

الدوري العراقي أُثبت بمعرف 5056 واسم Iraqi Premier League. طلب 2026-11-01 أعاد صفراً من أحداث اليوم، وطلب القادمة أعاد Al-Kahrabaa ضد Zakho بتاريخ 2026-11-05. طلب السابقة أعاد Erbil ضد Ghaz Al-Shamal بتاريخ 2026-10-31 مع نتيجة null وحالة NS غير محدثة؛ لم نحولها لنتيجة 0–0 أو مباراة منتهية/مباشرة.

الأهداف والتوقيت والبطاقات والتبديلات والتشكيلات والإحصاءات مرتبطة بما يعيده مزود تفاصيل المباراة. ليست كل الأحداث غنية بهذه البيانات، وبعض نقاط TheSportsDB مقيدة بالخطة المجانية. تظهر حالة عدم توفر البيانات ولا تُنشأ صفوف تخمينية.

[توثيق TheSportsDB وحدوده](https://www.thesportsdb.com/documentation) · [OpenLigaDB](https://www.openligadb.de/)
