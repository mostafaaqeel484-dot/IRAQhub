package com.smarthub.plus;

/** Shared duration contract for in-player and companion InfoBars. */
final class InfoBarDuration {
    static final String KEY = "osd_duration_seconds";
    static final int MIN_SECONDS = 1;
    static final int MAX_SECONDS = 15;
    static final int DEFAULT_SECONDS = 10;

    private InfoBarDuration() { }

    static int clamp(int seconds) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
    }

    static long millis(int seconds) {
        return clamp(seconds) * 1000L;
    }
}
