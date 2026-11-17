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

/** A compact non-focus-stealing alert visible over other apps including TV playback when permitted. */
public final class FloatingAlert {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager manager;
    private static View current;
    private static Runnable pendingHide;
    private FloatingAlert() {}

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
    private static GradientDrawable panel(Context context) {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xf7132337, 0xf71c3141});
        bg.setCornerRadius(dp(context, 18));
        bg.setStroke(dp(context, 2), 0xff36e0b8);
        return bg;
    }
    public static void show(Context context, FeedRepository.Feed feed) {
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT < 26 || !Settings.canDrawOverlays(app)) return;
        MAIN.post(() -> showOnMain(app, feed));
    }
    private static void showOnMain(Context context, FeedRepository.Feed f) {
        if (!Settings.canDrawOverlays(context)) return;
        hideOnMain();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

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
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(dp(context, 114), dp(context, 86));
        imageLp.rightMargin = dp(context, 11);
        banner.addView(photo, imageLp);
        if (f.imageUrl != null && !f.imageUrl.isEmpty()) {
            ImageLoader.load(context, photo, f.imageUrl);
        }

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        banner.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView newFeed = line(context, "●  فيد جديد  •  NEW FEED", 14, 0xff36e0b8, true);
        words.addView(newFeed);
        TextView name = line(context, f.name, 17, Color.WHITE, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(name);
        TextView frequency = line(context, f.satellite + "  •  " + f.frequencyText(),
                12, 0xffd2e8f1, false);
        frequency.setMaxLines(2);
        frequency.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(frequency);
        TextView badge = line(context, f.chroma + (f.isLive() ? "  •  ON AIR" : ""),
                11, 0xffffcb78, true);
        words.addView(badge);

        TextView close = line(context, "×", 25, 0xffd3dfe7, true);
        close.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(context, 30), dp(context, 48));
        banner.addView(close, closeLp);
        close.setOnClickListener(v -> hideOnMain());

        banner.setOnClickListener(v -> {
            Intent i = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("selected_feed", f.id());
            try { context.startActivity(i); } catch (Exception ignored) {}
            hideOnMain();
        });

        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(dp(context, 455), screen - dp(context, 26));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, dp(context, 110), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = dp(context, 20);
        params.y = dp(context, 28);
        params.setTitle("LiveFeed new DVB feed banner");
        try {
            wm.addView(banner, params);
            manager = wm;
            current = banner;
            pendingHide = FloatingAlert::hideOnMain;
            MAIN.postDelayed(pendingHide, 12_000);
        } catch (RuntimeException ignored) {
            current = null;
            manager = null;
        }
    }
    private static TextView line(Context ctx, String text, int size, int color, boolean bold) {
        TextView v = new TextView(ctx);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private static void hideOnMain() {
        if (pendingHide != null) MAIN.removeCallbacks(pendingHide);
        pendingHide = null;
        if (current != null && manager != null) {
            try { manager.removeView(current); } catch (Exception ignored) {}
        }
        current = null;
        manager = null;
    }
    public static void hide() { MAIN.post(FloatingAlert::hideOnMain); }
}
