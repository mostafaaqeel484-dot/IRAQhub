package com.smarthub.plus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.util.List;

/**
 * A permission-aware gateway to PUBLIC OEM DVB surfaces.
 *
 * Keeps Aurum and its DVB services installed. Never changes a tuner frequency,
 * channel database, firmware update or recording state without the user invoking
 * the actual manufacturer UI. The OEM's private PVR and system UID are NOT
 * recreated by these entry points.
 */
final class OemDvbFeatureGateway {
    static final String OEM = "com.tosmart.aurum";

    enum Status { AVAILABLE, MISSING, NOT_EXPORTED, PERMISSION_REQUIRED, DISABLED, OEM_PRIVATE }

    enum Feature {
        LOCAL_TV("IRAQhub DVB player", "مشغل IRAQhub الفضائي", "", false),
        LOCAL_CHANNELS("IRAQhub channels and favourites", "قنوات ومفضلة IRAQhub", "", false),
        LOCAL_EPG("IRAQhub programme guide", "دليل برامج IRAQhub", "", false),
        LOCAL_SIGNAL("Live signal and tuner", "إشارة التونر المباشرة", "", false),
        LOCAL_LNB("LNB profiles", "إعدادات LNB", "", false),

        OEM_LIVE_TV("Original DVB television", "التلفاز الفضائي الأصلي", "", false),
        OEM_MENU("Original DVB main menu", "القائمة الأصلية للقنوات", "", false),
        OEM_EPG("Native EPG Pro", "دليل البرامج الأصلي EPG", "", false),
        OEM_TIMERS("Native event timer / scheduled actions", "مؤقت برامج الرسيفر الأصلي", "", false),
        OEM_SATELLITES("Satellite / frequency / channel scan", "الأقمار والترددات والبحث", "satellite", false),
        OEM_MOTOR("DiSEqC / USALS motor", "الموتور DiSEqC / USALS", "motor", false),
        OEM_EDIT_TV("Edit TV channels", "تعديل القنوات التلفزيونية", "edit_tv", false),
        OEM_EDIT_RADIO("Edit radio channels", "تعديل قنوات الراديو", "edit_radio", false),
        OEM_FAVOURITES("Edit favourites", "تعديل المفضلة", "edit_favorites", false),
        OEM_SORT("Sort channels", "ترتيب القنوات", "sort_channels", false),
        OEM_BACKUP("Channel backup / restore", "نسخ واستعادة القنوات", "channel_backup", false),
        OEM_SERVER("Firmware server settings", "إعدادات سيرفر الجهاز", "server", false),
        OEM_CA("Firmware CA status", "قائمة CA الأصلية", "ca", false),
        OEM_PATCH("Firmware patch menu", "قائمة Patch الأصلية", "patch", false),
        OEM_HOMESHARE("Firmware Home Share", "مشاركة Home Share", "homeshare", false),
        OEM_USB_FILES("USB file manager", "مدير ملفات USB", "usb_files", false),
        OEM_USB_MEDIA("USB media player", "مشغل وسائط USB", "usb_media", false),
        OEM_ALL_APPS("Original app manager", "مدير تطبيقات الجهاز", "", false),
        OEM_WALLPAPER("Original wallpaper settings", "خلفيات الجهاز الأصلية", "", false),
        OEM_NET_UPDATE("Firmware network updater", "تحديث النظام عبر الشبكة", "", true),
        OEM_FILE_UPDATE("Firmware USB updater", "تحديث النظام عبر USB", "", true),
        OEM_HOME("Original Aurum home", "واجهة Aurum الأصلية", "", false),
        HOME_CHOICE("Select default HOME app", "اختيار اللانشر الافتراضي", "", false),
        OEM_PVR_PRIVATE("Direct PVR recording (private OEM API)", "التسجيل المباشر PVR (واجهة نظام خاصة)", "", false),
        OEM_TIMESHIFT_PRIVATE("Direct TimeShift (private OEM API)", "الإيقاف المؤقت TimeShift (واجهة خاصة)", "", false);

        final String en, ar, manufacturerId;
        final boolean confirmationRequired;
        Feature(String en, String ar, String manufacturerId, boolean confirmationRequired) {
            this.en = en; this.ar = ar;
            this.manufacturerId = manufacturerId;
            this.confirmationRequired = confirmationRequired;
        }
        String label(boolean arabic) { return arabic ? ar : en; }
        boolean manufacturerRoute() { return !manufacturerId.isEmpty(); }
    }

    private OemDvbFeatureGateway() {}

    static Intent intent(Context context, Feature feature) {
        switch (feature) {
            case LOCAL_TV: return new Intent(context, DvbPlaybackActivity.class);
            case LOCAL_CHANNELS: return new Intent(context, ReceiverChannelsActivity.class);
            case LOCAL_EPG: return new Intent(context, SatelliteGuideActivity.class);
            case LOCAL_SIGNAL: return new Intent(context, SignalInformationActivity.class);
            case LOCAL_LNB: return new Intent(context, TunerSettingsActivity.class);
            case OEM_LIVE_TV: return ReceiverNavigation.television();
            case OEM_MENU: return ReceiverNavigation.menu();
            case OEM_EPG: return ReceiverNavigation.guide();
            case OEM_TIMERS: return new Intent("com.tosmart.action.home.timer").setPackage(OEM);
            case OEM_ALL_APPS: return new Intent("com.tosmart.launcher.aurum.app").setPackage(OEM);
            case OEM_WALLPAPER: return new Intent("com.tosmart.launcher.ui.CHANGE_WALLPAPER").setPackage(OEM);
            case OEM_NET_UPDATE: return new Intent("com.tosmart.action.update.netupdate").setPackage(OEM);
            case OEM_FILE_UPDATE: return new Intent("com.tosmart.action.update.ext_file").setPackage(OEM);
            case OEM_HOME: return ReceiverNavigation.originalHome();
            case HOME_CHOICE: return new Intent(Settings.ACTION_HOME_SETTINGS);
            default: return null;
        }
    }

    static Status status(Context context, Feature feature) {
        if (feature == Feature.OEM_PVR_PRIVATE || feature == Feature.OEM_TIMESHIFT_PRIVATE)
            return Status.OEM_PRIVATE;
        if (feature.manufacturerRoute()) {
            ManufacturerFeatures.Entry entry = ManufacturerFeatures.find(context, feature.manufacturerId);
            if (entry == null) return Status.MISSING;
            String reason = ManufacturerFeatures.unavailable(context, entry);
            if (reason.isEmpty()) return Status.AVAILABLE;
            if ("OEM_PERMISSION_REQUIRED".equals(reason)) return Status.PERMISSION_REQUIRED;
            if ("PRIVATE_OEM_COMPONENT".equals(reason)) return Status.NOT_EXPORTED;
            if ("DISABLED".equals(reason)) return Status.DISABLED;
            return Status.MISSING;
        }
        Intent request = intent(context, feature);
        if (request == null) return Status.MISSING;
        try {
            ResolveInfo result = context.getPackageManager().resolveActivity(request, 0);
            if (result == null || result.activityInfo == null) return Status.MISSING;
            ActivityInfo activity = result.activityInfo;
            if (!activity.enabled || !activity.applicationInfo.enabled) return Status.DISABLED;
            if (!activity.exported && !context.getPackageName().equals(activity.packageName))
                return Status.NOT_EXPORTED;
            if (activity.permission != null
                    && context.checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED)
                return Status.PERMISSION_REQUIRED;
            return Status.AVAILABLE;
        } catch (RuntimeException unavailable) {
            return Status.MISSING;
        }
    }

    static boolean open(Context context, Feature feature) {
        if (status(context, feature) != Status.AVAILABLE) return false;
        if (feature.manufacturerRoute())
            return ManufacturerFeatures.open(context, ManufacturerFeatures.find(context, feature.manufacturerId));
        Intent request = intent(context, feature);
        if (request == null) return false;
        if (!(context instanceof Activity)) request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(request);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    static String statusLabel(Status status, boolean arabic) {
        switch (status) {
            case AVAILABLE: return arabic ? "مسار متاح — لم يُختبر على الجهاز" : "Route available — untested on hardware";
            case NOT_EXPORTED: return arabic ? "واجهة OEM داخلية" : "Private OEM component";
            case PERMISSION_REQUIRED: return arabic ? "تحتاج صلاحية OEM" : "OEM permission required";
            case DISABLED: return arabic ? "معطّلة في الروم" : "Disabled in firmware";
            case OEM_PRIVATE: return arabic ? "تحتاج SDK / صلاحية نظام" : "Requires system SDK / privilege";
            default: return arabic ? "غير متاح على هذا الجهاز" : "Not exposed on this device";
        }
    }

    /**
     * Device-only diagnostics. Never reports private content, serial numbers,
     * recordings, CA keys or proprietary channel databases.
     */
    static String report(Context context) {
        boolean arabic = context.getSharedPreferences("smarthub", 0).getBoolean("arabic", true);
        PackageManager pm = context.getPackageManager();
        StringBuilder output = new StringBuilder("IRAQhub / Aurum DVB integration\n");
        output.append("Android API ").append(Build.VERSION.SDK_INT).append('\n');
        try {
            pm.getPackageInfo(OEM, 0);
            output.append("Aurum installed: yes\n");
        } catch (PackageManager.NameNotFoundException e) {
            output.append("Aurum installed: no\n");
        }
        output.append("IRAQhub HOME selected: ");
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo defaultHome = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            output.append(defaultHome != null && defaultHome.activityInfo != null
                    && context.getPackageName().equals(defaultHome.activityInfo.packageName) ? "yes" : "no / chooser").append('\n');
        } catch (Exception e) { output.append("unknown\n"); }
        output.append("System UID: ").append(Process.myUid() == Process.SYSTEM_UID ? "yes" : "no").append('\n');
        reportPermission(output, context, "com.android.providers.tv.permission.READ_EPG_DATA");
        reportPermission(output, context, "com.android.providers.tv.permission.WRITE_EPG_DATA");
        reportPermission(output, context, "com.tosmart.dvb.common.permission.READ_PROGRAM_INDEX");
        reportPermission(output, context, "com.dvb.service.permission.READ_CONFIG");
        reportPermission(output, context, "com.dvb.service.permission.WRITE_CONFIG");
        output.append("Android TV Input recording: ");
        try {
            TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            List<TvInputInfo> inputs = manager == null ? null : manager.getTvInputList();
            int recording = 0;
            if (inputs != null) for (TvInputInfo item : inputs) if (item.canRecord()) recording++;
            output.append(inputs == null ? "unavailable" : recording + " recording-capable of " + inputs.size() + " TV inputs");
        } catch (SecurityException e) { output.append("permission denied"); }
        catch (RuntimeException e) { output.append("unavailable"); }
        output.append("\n\n").append(arabic ? "المسارات المنشورة (مو ضمان نجاح التنفيذ):\n" : "Published routes (not a hardware pass):\n");
        for (Feature feature : Feature.values()) {
            output.append("• ").append(feature.label(arabic)).append(": ")
                    .append(statusLabel(status(context, feature), arabic)).append('\n');
        }
        output.append(arabic
                ? "\nالتسجيل المباشر وTimeShift والتعامل كـ android.uid.system لا يمكن نقلها بنسخ APK. يلزم SDK الشركة أو روم بتوقيع مناسب. لا تحذف Aurum الأصلي.\n"
                : "\nDirect PVR/TimeShift and android.uid.system cannot be inherited by copying an APK. OEM SDK/platform signing is required. Keep Aurum installed.\n");
        return output.toString();
    }

    private static void reportPermission(StringBuilder output, Context c, String permission) {
        output.append(permission).append(": ")
              .append(c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "granted" : "denied")
              .append('\n');
    }
}
