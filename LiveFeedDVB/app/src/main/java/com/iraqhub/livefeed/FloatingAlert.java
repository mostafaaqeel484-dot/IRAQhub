package com.iraqhub.livefeed;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A user-authorized, non-focus-stealing DVB notification overlay.
 * Two simultaneous new feeds: top-right and bottom-right for exactly 10 seconds.
 * Further feeds are shown in subsequent pairs so none is immediately overwritten.
 */
public final class FloatingAlert {
    public static final long DISPLAY_MS = 10_000L;
    public static final int MAX_QUEUED = 30;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<FeedRepository.Feed> QUEUE = new ArrayDeque<>();
    private static final List<View> ACTIVE = new ArrayList<>(2);
    private static WindowManager manager;
    private static Runnable timeout;
    private static Context appContext;
    private FloatingAlert() {}

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
    private static GradientDrawable panel(Context context) {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xf7132337, 0xf71c3141});
        bg.setCornerRadius(dp(context, 18));
        bg.setStroke(dp(context, 2), 0xff36e0b8);
        return bg;
    }

    public static void show(Context context, FeedRepository.Feed feed) {
        showBatch(context, Collections.singletonList(feed));
    }

    public static void showBatch(Context context, List<FeedRepository.Feed> newFeeds) {
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT < 26 || !Settings.canDrawOverlays(app)
                || newFeeds == null || newFeeds.isEmpty()) return;
        List<FeedRepository.Feed> snapshot = new ArrayList<>(newFeeds);
        MAIN.post(() -> {
            if (!Settings.canDrawOverlays(app)) return;
            appContext = app;
            for (FeedRepository.Feed feed : snapshot) {
                if (feed != null && QUEUE.size() < MAX_QUEUED) QUEUE.addLast(feed);
            }
            if (ACTIVE.isEmpty()) showNextPair();
        });
    }

    private static void showNextPair() {
        if (appContext == null || QUEUE.isEmpty()) return;
        manager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) { QUEUE.clear(); return; }
        for (int slot = 0; slot < 2 && !QUEUE.isEmpty(); slot++) {
            FeedRepository.Feed feed = QUEUE.removeFirst();
            View banner = createBanner(appContext, feed);
            int screenWidth = appContext.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(dp(appContext, 455), screenWidth - dp(appContext, 26));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    width, dp(appContext, 110), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = (slot == 0 ? Gravity.TOP : Gravity.BOTTOM) | Gravity.RIGHT;
            params.x = dp(appContext, 20);
            params.y = dp(appContext, 28);
            params.setTitle("LiveFeed DVB • new satellite feed " + (slot == 0 ? "top" : "bottom"));
            try {
                manager.addView(banner, params);
                ACTIVE.add(banner);
            } catch (RuntimeException ignored) {
                // Some OEMs block overlays over protected playback. No elevated access attempted.
            }
        }
        if (!ACTIVE.isEmpty()) {
            timeout = FloatingAlert::dismissPair;
            MAIN.postDelayed(timeout, DISPLAY_MS);
        } else {
            QUEUE.clear();
        }
    }

    private static View createBanner(Context context, FeedRepository.Feed f) {
        LinearLayout banner = new LinearLayout(context);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        banner.setBackground(panel(context));
        banner.setElevation(dp(context, 12));

        ImageView photo = new ImageView(context);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageResource(R.drawable.ic_satellite);
        GradientDrawable photoBg = new GradientDrawable();
        photoBg.setColor(0xff102837);
        photoBg.setCornerRadius(dp(context, 12));
        photo.setBackground(photoBg);
        photo.setClipToOutline(true);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(
                dp(context, 114), dp(context, 86));
        imageLp.rightMargin = dp(context, 11);
        banner.addView(photo, imageLp);
        if (f.imageUrl != null && !f.imageUrl.isEmpty())
            ImageLoader.load(context, photo, f.imageUrl);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        banner.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        words.addView(line(context, f.isReported()
                        ? "◉  بلاغ عن فيد  •  REPORTED" : "●  فيد جديد  •  NEW FEED",
                14, 0xff36e0b8, true));

        TextView name = line(context, f.name == null ? "SATELLITE FEED" : f.name,
                17, Color.WHITE, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(name);
        TextView frequency = line(context, f.satellite + "  •  " + f.frequencyText(),
                12, 0xffd2e8f1, false);
        frequency.setMaxLines(2);
        frequency.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(frequency);
        words.addView(line(context,
                (f.chroma == null ? "—" : f.chroma) + (f.isLive() ? "  •  ON AIR" : ""),
                11, 0xffffcb78, true));

        TextView close = line(context, "×", 25, 0xffd3dfe7, true);
        close.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        banner.addView(close, new LinearLayout.LayoutParams(dp(context, 30), dp(context, 48)));
        close.setOnClickListener(v -> dismissPair());

        banner.setOnClickListener(v -> {
            Intent open = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("selected_feed", f.id());
            try { context.startActivity(open); } catch (Exception ignored) {}
            dismissPair();
        });
        return banner;
    }

    private static TextView line(Context ctx, String value, int size, int color, boolean bold) {
        TextView view = new TextView(ctx);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static void dismissPair() {
        if (timeout != null) MAIN.removeCallbacks(timeout);
        timeout = null;
        if (manager != null) for (View view : ACTIVE) {
            try { manager.removeView(view); } catch (RuntimeException ignored) {}
        }
        ACTIVE.clear();
        if (!QUEUE.isEmpty()) showNextPair();
    }

    public static void hide() {
        MAIN.post(() -> {
            QUEUE.clear();
            dismissPair();
            appContext = null;
            manager = null;
        });
    }
}
