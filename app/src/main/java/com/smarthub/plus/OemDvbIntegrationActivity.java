package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One remote-friendly entry point to the original DVB functions.
 * The feature's destination belongs to the OEM application; this screen
 * never fabricates scan/record/motor results or issues undocumented commands.
 */
public final class OemDvbIntegrationActivity extends HubActivity {
    private volatile boolean destroyed;
    private volatile String reportText = "";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout body = page(tx("تكامل IRAQhub مع رسيفر Aurum", "IRAQhub / Aurum DVB integration"),
                tx("القنوات الأصلية والبحث وEPG ومؤقت الجهاز بصلاحيات الروم الفعلية",
                        "Native channels, scan, EPG and event timer with actual firmware permissions"));

        LinearLayout info = card(body);
        info.addView(text(tx("ابقِ Aurum مثبتاً — IRAQhub يبقى HOME والواجهة الأساسية.",
                "Keep Aurum installed — IRAQhub remains your HOME interface."), 17, true));
        note(info, tx("كل زر يتحقق أولاً من وجود الواجهة وصلاحية فتحها. نجاح فتح القائمة ليس دليلاً على عمل العتاد؛ اختبر على الرسيفر. لا ننسخ هوية النظام أو نعدل قواعد قنوات المصنع.",
                "Each route is checked before opening. Menu availability does not prove tuner support; test on the receiver. System identity and OEM channel database are not modified."));

        action(card(body),tx("فهرس كامل لكل وظائف Aurum الأصلية","Full original Aurum receiver feature index"),()->startActivity(new android.content.Intent(this,OemFullSettingsActivity.class)));
        group(body, tx("مشغل IRAQhub وقنواته", "IRAQhub playback and channels"),
                OemDvbFeatureGateway.Feature.LOCAL_TV,
                OemDvbFeatureGateway.Feature.LOCAL_CHANNELS,
                OemDvbFeatureGateway.Feature.LOCAL_EPG,
                OemDvbFeatureGateway.Feature.LOCAL_SIGNAL,
                OemDvbFeatureGateway.Feature.LOCAL_LNB);

        group(body, tx("التلفاز والقنوات الأصلية", "Native television and channel management"),
                OemDvbFeatureGateway.Feature.OEM_LIVE_TV,
                OemDvbFeatureGateway.Feature.OEM_MENU,
                OemDvbFeatureGateway.Feature.OEM_SATELLITES,
                OemDvbFeatureGateway.Feature.OEM_EDIT_TV,
                OemDvbFeatureGateway.Feature.OEM_EDIT_RADIO,
                OemDvbFeatureGateway.Feature.OEM_FAVOURITES,
                OemDvbFeatureGateway.Feature.OEM_SORT,
                OemDvbFeatureGateway.Feature.OEM_BACKUP,
                OemDvbFeatureGateway.Feature.OEM_MOTOR);

        group(body, tx("دليل البرامج والتسجيل", "Guide and recording"),
                OemDvbFeatureGateway.Feature.OEM_EPG,
                OemDvbFeatureGateway.Feature.OEM_TIMERS,
                OemDvbFeatureGateway.Feature.OEM_PVR_PRIVATE,
                OemDvbFeatureGateway.Feature.OEM_TIMESHIFT_PRIVATE);
        note(card(body), tx("مؤقت الأحداث الأصلي متاح عبر واجهة منشورة. التسجيل الفعلي وTimeShift ليس لهما مدخل خارجي عام مؤكد داخل APK المفحوص؛ لا يمكن ادعاء تشغيلهما من IRAQhub بدون SDK الروم.",
                "The public event timer is available where installed. Direct PVR and TimeShift have no confirmed public external entry in the inspected APK; they require an OEM API or platform integration."));

        group(body, tx("خدمات المصنع والوسائط", "Firmware and media"),
                OemDvbFeatureGateway.Feature.OEM_SERVER,
                OemDvbFeatureGateway.Feature.OEM_CA,
                OemDvbFeatureGateway.Feature.OEM_PATCH,
                OemDvbFeatureGateway.Feature.OEM_BISS_INPUT,
                OemDvbFeatureGateway.Feature.OEM_HOMESHARE,
                OemDvbFeatureGateway.Feature.OEM_USB_FILES,
                OemDvbFeatureGateway.Feature.OEM_USB_MEDIA,
                OemDvbFeatureGateway.Feature.OEM_ALL_APPS,
                OemDvbFeatureGateway.Feature.OEM_WALLPAPER);

        note(card(body), tx("مفتاح BISS يُكتب في محرر BisKeyEditPop داخل Aurum، بعد اختيار BISS من Patch Menu. IRAQhub ما يخزّن المفاتيح ولا يرسلها لخدمات غير موثقة.",
                "Enter the BISS key in Aurum’s own BisKeyEditPop editor from Patch Menu. IRAQhub does not copy or persist keys."));

        group(body, tx("تحديث النظام واللانشر", "Updates and HOME"),
                OemDvbFeatureGateway.Feature.OEM_NET_UPDATE,
                OemDvbFeatureGateway.Feature.OEM_FILE_UPDATE,
                OemDvbFeatureGateway.Feature.HOME_CHOICE,
                OemDvbFeatureGateway.Feature.OEM_HOME);

        LinearLayout diag = card(body);
        diag.addView(text(tx("تقرير صلاحيات الجهاز", "Device permissions report"), 21, true));
        TextView textReport = text(tx("جارٍ فحص صلاحيات النظام والواجهات…",
                "Checking firmware routes and permissions…"), 13, false);
        diag.addView(textReport);
        Button copy = action(diag, tx("نسخ تقرير التشخيص", "Copy diagnostics"),
                () -> {
                    if (reportText.isEmpty()) return;
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("IRAQhub DVB report", reportText));
                    toast(tx("تم نسخ تقرير التشخيص", "Diagnostics copied"));
                });
        copy.setEnabled(false);
        new Thread(() -> {
            String result;
            try { result = OemDvbFeatureGateway.report(getApplicationContext()); }
            catch (RuntimeException error) { result = "DVB diagnostics unavailable: " + error.getClass().getSimpleName(); }
            final String done = result;
            runOnUiThread(() -> {
                if (!destroyed) {
                    reportText = done;
                    textReport.setText(done);
                    copy.setEnabled(true);
                }
            });
        }, "iraqhub-dvb-diagnostics").start();
    }

    private void group(LinearLayout body, String header, OemDvbFeatureGateway.Feature... entries) {
        LinearLayout section = card(body);
        section.addView(text(header, 21, true));
        for (OemDvbFeatureGateway.Feature feature : entries) {
            OemDvbFeatureGateway.Status state = OemDvbFeatureGateway.status(this, feature);
            Button button = action(section, feature.label(ar), () -> confirmAndOpen(feature));
            button.setEnabled(state == OemDvbFeatureGateway.Status.AVAILABLE);
            if (state != OemDvbFeatureGateway.Status.AVAILABLE)
                note(section, OemDvbFeatureGateway.statusLabel(state, ar));
        }
    }

    private void confirmAndOpen(OemDvbFeatureGateway.Feature feature) {
        Runnable open = () -> {
            if (!OemDvbFeatureGateway.open(this, feature))
                toast(tx("هذه الوظيفة غير متاحة أو رفضها النظام",
                        "The firmware denied this route or it is unavailable"));
        };
        if (!feature.confirmationRequired) { open.run(); return; }
        new AlertDialog.Builder(this)
                .setTitle(feature.label(ar))
                .setMessage(tx("راح تفتح واجهة تحديث الروم الأصلية. لا تبدأ أي تحديث إلا بملف رسمي مناسب لهذا الرسيفر، وتأكد من عدم انقطاع الكهرباء.",
                        "This opens the official firmware updater. Only flash a verified image for this exact receiver and ensure uninterrupted power."))
                .setPositiveButton(tx("فتح الواجهة", "Open updater"), (d, which) -> open.run())
                .setNegativeButton(tx("إلغاء", "Cancel"), null)
                .show();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }
}
