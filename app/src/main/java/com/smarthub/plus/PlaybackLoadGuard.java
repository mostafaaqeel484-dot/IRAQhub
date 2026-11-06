package com.smarthub.plus;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Protects live DVB and demanding 4:2:2 / HEVC range-extension playback.
 * WorkManager retries deferred sync; EPG and poster caches stay untouched.
 */
final class PlaybackLoadGuard {
    private PlaybackLoadGuard() {}

    static boolean shouldDefer(boolean dvbVisible, boolean iptvVisible,
            boolean confirmed422, boolean hevcRextCandidate, boolean lowRam) {
        return (dvbVisible || iptvVisible) && (confirmed422 || hevcRextCandidate || lowRam);
    }

    static boolean shouldDeferSync(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("smarthub", Context.MODE_PRIVATE);
        boolean lowRam = false;
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(info);
                lowRam = manager.isLowRamDevice() || info.lowMemory;
            }
        } catch (RuntimeException ignored) { /* Fall back to stream profile. */ }
        if (prefs.getBoolean("dvb_tuning",false)) return true;
        return shouldDefer(
                prefs.getBoolean("dvb_player_visible", false),
                prefs.getBoolean("iptv_player_visible", false),
                prefs.getBoolean("decoder_input_profile422", false) || prefs.getBoolean("dvb_422_compat_active",false),
                prefs.getBoolean("decoder_422_possible", false),
                lowRam);
    }
}
