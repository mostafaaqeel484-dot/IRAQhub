#!/usr/bin/env bash
# IRAQhub 3.7.3 — optional, read-only ADB evidence collection.
# Usage: bash scripts/x5m_422_acceptance.sh <adb-device-serial> [output-directory]
# Works with USB ADB or a previously paired TCP device. No rooting, package
# removal, changes to DVB services, or BISS/key exports.
set -euo pipefail
TARGET="${1:-}"
if [[ -z "$TARGET" ]]; then
  echo "Provide the exact ADB serial from: adb devices -l" >&2
  exit 2
fi
OUTPUT="${2:-./IRAQhub_X5M_Evidence}"
mkdir -p "$OUTPUT"
ADB=(adb -s "$TARGET")
if ! "${ADB[@]}" get-state 2>/dev/null | grep -q '^device$'; then
  echo "ADB device is not authorised/connected: $TARGET" >&2
  exit 3
fi
echo "Collecting read-only X5M diagnostics. Start a DVB 4:2:2 channel first."
{
 echo "IRAQhub X5M 4:2:2 hardware acceptance snapshot"
 date -u '+UTC %Y-%m-%dT%H:%M:%SZ'
 for prop in ro.build.version.release ro.build.version.sdk ro.product.model ro.hardware ro.soc.model; do
   printf '%s=' "$prop"
   "${ADB[@]}" shell getprop "$prop" 2>/dev/null | tr -d '\r'
 done
} > "$OUTPUT/device.txt"
"${ADB[@]}" shell dumpsys meminfo com.smarthub.plus > "$OUTPUT/iraqhub_meminfo.txt" 2>&1 || true
"${ADB[@]}" shell dumpsys media.codec > "$OUTPUT/media_codec.txt" 2>&1 || true
"${ADB[@]}" shell dumpsys SurfaceFlinger --latency-clear 2>/dev/null >/dev/null || true
"${ADB[@]}" shell dumpsys SurfaceFlinger > "$OUTPUT/surfaceflinger_summary.txt" 2>&1 || true
cat > "$OUTPUT/TEST_CHECKLIST_AR.txt" <<'EOF'
IRAQhub 3.7.3 — تحقق على Amlogic X5M
[ ] H.264 4:2:2 10-bit: هل ظهرت صورة فعلية وليس شاشة سوداء؟
[ ] HEVC 4:2:2 10-bit: هل ظهرت صورة فعلية وليس شاشة سوداء؟
[ ] هل الألوان صحيحة؟ لون البشرة، الأحمر، الأزرق، الأخضر؟
[ ] راقب الصوت ومزامنته خلال 10 دقائق من قناة حقيقية
[ ] اضغط INFO ثم انسخ تقرير الجهاز من قائمة التوافق في IRAQhub
[ ] البحث اليدوي: جرّب TP معروفاً وسجّل Status / capture_bytes / error
[ ] استيراد DB: جرّب معاينة ثم دمج قاعدة مُصدّرة مع نسخة احتياطية
[ ] EPG: بوستر فيلم / مسلسل / مباراة حالية وقادمة
[ ] CHMAX: شعار قناة موثوقة بعد إعادة تشغيل التطبيق
[ ] تأكد من أن اللوحة الأمامية لا تتلقى أوامر تخمينية
ملاحظة: ظهور Decoder أو إطارات غير كافٍ لإثبات سلامة 4:2:2.
هذه الأداة لا تختبر جودة الصورة تلقائياً ولا تصلح صلاحيات Firmware.
EOF
echo "Evidence written to: $OUTPUT"
echo "Check and redact file contents before sharing; diagnostics can include app names."
