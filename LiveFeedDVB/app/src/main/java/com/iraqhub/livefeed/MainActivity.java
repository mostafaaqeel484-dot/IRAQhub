package com.iraqhub.livefeed;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 16, 28);
    private static final int PANEL = Color.rgb(21, 31, 50);
    private static final int LIGHT = Color.rgb(232, 242, 249);
    private static final int MUTED = Color.rgb(144, 165, 188);
    private static final int ACCENT = Color.rgb(60, 222, 178);
    private static final int WARNING = Color.rgb(255, 184, 85);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<FeedRepository.Feed> feeds = new ArrayList<>();
    private final List<TextView> filterViews = new ArrayList<>();
    private SharedPreferences preferences;
    private GridLayout cards;
    private ScrollView page;
    private TextView status, heading, notifyButton, overlayButton;
    private EditText searchBox;
    private String filter = "ALL", satelliteFilter = "", query = "";
    private boolean running, busy;
    private int displayLimit = 72;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (running) {
                refresh(false);
                handler.postDelayed(this, 20_000);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        preferences = getSharedPreferences("livefeed_settings", Context.MODE_PRIVATE);
        FeedRepository.createChannel(this);
        FeedJobService.schedule(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        makeLayout();
        refresh(false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (preferences.getBoolean("overlay", false)) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayMonitor();
            } else {
                preferences.edit().putBoolean("overlay", false).apply();
                stopService(new Intent(this, FeedMonitorService.class));
            }
        }
        showOverlayState();
        running = true;
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 20_000);
    }
    @Override protected void onPause() {
        running = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String id = intent.getStringExtra("selected_feed");
        if (id != null && !feeds.isEmpty()) {
            for (FeedRepository.Feed f : feeds) {
                if (id.equals(f.id())) {
                    intent.removeExtra("selected_feed");
                    details(f);
                    break;
                }
            }
        }
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(poll);
        executor.shutdownNow();
        super.onDestroy();
    }

    private int dp(float d) { return Math.round(getResources().getDisplayMetrics().density * d); }
    private GradientDrawable shape(int color, float radius) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color);
        b.setCornerRadius(dp(radius));
        return b;
    }
    private GradientDrawable outline(int color, int stroke) {
        GradientDrawable b = shape(color, 17);
        b.setStroke(dp(1), stroke);
        return b;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(true);
        return v;
    }
    private void focusable(View view, int normal) {
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(outline(focused ? 0xff25435a : normal, focused ? ACCENT : 0xff273954));
            v.setElevation(dp(focused ? 10 : 2));
            v.setScaleX(focused ? 1.015f : 1f);
            v.setScaleY(focused ? 1.015f : 1f);
        });
    }

    private void makeLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(12), dp(18), dp(10));
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
        toolbar.addView(brand, bp);
        TextView logo = text("◉  LIVEFEED DVB", 22, LIGHT, true);
        logo.setLetterSpacing(0.02f);
        brand.addView(logo);
        heading = text("Satellite Feed Hunter  •  Android TV", 12, MUTED, false);
        brand.addView(heading);
        notifyButton = button("", () -> {
            boolean on = !preferences.getBoolean("notify", true);
            preferences.edit().putBoolean("notify", on).apply();
            showNotificationState();
            toast(on ? "تم تفعيل إشعارات الفيدات" : "تم إيقاف الإشعارات");
        });
        toolbar.addView(notifyButton, sizeMargin(dp(90), dp(43), 5));
        showNotificationState();
        overlayButton = button("", this::toggleOverlay);
        toolbar.addView(overlayButton, sizeMargin(dp(106), dp(43), 5));
        overlayButton.setOnLongClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) { toast("فعّل صلاحية الظهور فوق التطبيقات أولاً"); return true; }
            FeedRepository.Feed example = new FeedRepository.Feed();
            example.name = "LIVE FEED • تجربة الإشعار";
            example.satellite = "Eutelsat 7E";
            example.frequency = "11000";
            example.polarization = "H";
            example.symbolRate = "7200";
            example.status = "ON";
            example.chroma = "4:2:2";
            example.imageUrl = "";
            FloatingAlert.show(this, example);
            return true;
        });
        showOverlayState();
        TextView guide = button("المباريات", () -> openSite("https://live-feed.net/tv-guide"));
        toolbar.addView(guide, sizeMargin(dp(104), dp(43), 5));
        TextView reload = button("⟳ تحديث", () -> refresh(true));
        toolbar.addView(reload, sizeMargin(dp(106), dp(43), 5));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(tools, new LinearLayout.LayoutParams(-1, dp(55)));
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        tools.addView(filterScroll, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        filterScroll.addView(chips);
        addFilter(chips, "الكل", "ALL");
        addFilter(chips, "🟢 مباشر", "ON");
        addFilter(chips, "🎥 4:2:2", "422");
        addFilter(chips, "★ المفضلة", "FAV");
        TextView sats = button("🛰 الأقمار", this::chooseSatellite);
        tools.addView(sats, sizeMargin(dp(118), dp(42), 4));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextColor(LIGHT);
        searchBox.setHintTextColor(MUTED);
        searchBox.setTextSize(14);
        searchBox.setHint("⌕   ابحث عن فيد أو تردد أو قمر");
        searchBox.setPadding(dp(15), 0, dp(15), 0);
        searchBox.setBackground(outline(PANEL, 0xff2a4157));
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        tools.addView(searchBox, sizeMargin(dp(258), dp(44), 5));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                query = s.toString().toLowerCase(Locale.ROOT).trim();
                displayLimit = 72;
                render();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        status = text("جارِ الاتصال بـ Live-Feed.net…", 12, MUTED, false);
        status.setPadding(dp(8), dp(4), 0, dp(7));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(33)));

        page = new ScrollView(this);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        cards = new GridLayout(this);
        cards.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        page.addView(cards, new ScrollView.LayoutParams(-1, -2));

        TextView footer = text("المصادر: Live-Feed.net + Satelliweb  •  ترددات فريدة بدون تكرار  •  فحص أساسي 20 ثانية", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(25)));
        updateFilterStyles();
    }
    private LinearLayout.LayoutParams sizeMargin(int w, int h, int ml) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(ml), 0, 0, 0);
        return p;
    }
    private TextView button(String value, Runnable action) {
        TextView v = text(value, 13, LIGHT, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(outline(PANEL, 0xff2a3b57));
        focusable(v, PANEL);
        v.setOnClickListener(w -> action.run());
        return v;
    }
    private void addFilter(LinearLayout row, String caption, String id) {
        TextView v = button(caption, () -> {
            filter = id;
            displayLimit = 72;
            updateFilterStyles();
            render();
        });
        v.setTag(id);
        v.setPadding(dp(15), 0, dp(15), 0);
        row.addView(v, sizeMargin(-2, dp(41), 5));
        filterViews.add(v);
    }
    private void showNotificationState() {
        if (notifyButton != null) notifyButton.setText(preferences.getBoolean("notify", true) ? "🔔 تنبيه" : "🔕 مطفأ");
    }
    private void showOverlayState() {
        if (overlayButton != null) overlayButton.setText(
                preferences.getBoolean("overlay", false) && Settings.canDrawOverlays(this) ? "▣ عائم ON" : "▣ عائم OFF");
    }
    private void toggleOverlay() {
        if (preferences.getBoolean("overlay", false)) {
            preferences.edit().putBoolean("overlay", false).apply();
            stopService(new Intent(this, FeedMonitorService.class));
            FloatingAlert.hide();
            showOverlayState();
            toast("توقفت التنبيهات العائمة");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, 2391);
                toast("اسمح بالظهور فوق التطبيقات ثم ارجع للتطبيق");
            } catch (Exception e) {
                toast("تعذر فتح إعدادات صلاحية الظهور فوق التطبيقات");
            }
        } else {
            enableOverlay();
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2391) {
            if (Settings.canDrawOverlays(this)) enableOverlay();
            else { showOverlayState(); toast("التنبيهات العائمة تحتاج صلاحية الظهور فوق التطبيقات"); }
        }
    }
    private void enableOverlay() {
        preferences.edit().putBoolean("overlay", true).apply();
        if (!startOverlayMonitor()) {
            preferences.edit().putBoolean("overlay", false).apply();
        }
        showOverlayState();
    }
    private boolean startOverlayMonitor() {
        try {
            Intent service = new Intent(this, FeedMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            return true;
        } catch (RuntimeException error) {
            toast("تعذر بدء مراقبة الفيدات: " + error.getClass().getSimpleName());
            return false;
        }
    }
    private void updateFilterStyles() {
        for (TextView v : filterViews) {
            boolean selected = filter.equals(v.getTag());
            v.setTextColor(selected ? BG : LIGHT);
            v.setBackground(outline(selected ? ACCENT : PANEL, selected ? ACCENT : 0xff2a3b57));
        }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void openSite(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("ماكو متصفح مناسب مثبت"); }
    }

    private void refresh(boolean manual) {
        if (busy) return;
        busy = true;
        if (status != null) status.setText("⟳  جارِ تحديث الفيدات من الموقع…");
        executor.execute(() -> {
            try {
                List<FeedRepository.Feed> fetched = MultiSourceFeeds.fetch(manual);
                FeedRepository.processNew(getApplicationContext(), fetched);
                handler.post(() -> {
                    feeds.clear();
                    feeds.addAll(fetched);
                    Collections.sort(feeds, Comparator.comparingInt((FeedRepository.Feed f) ->
                                    f.isLive() ? 0 : (f.isReported() ? 1 : 2))
                            .thenComparing(f -> f.satellite).thenComparing(f -> f.frequency));
                    int on = 0, reported = 0, mode422 = 0;
                    for (FeedRepository.Feed f : feeds) {
                        if (f.isLive()) on++;
                        if (f.isReported()) reported++;
                        if (f.is422()) mode422++;
                    }
                    status.setText("🟢 " + on + " مباشر   •   " + reported + " بلاغ نشط   •   "
                            + feeds.size() + " تردد فريد   •   " + mode422 + " 4:2:2   •   مصدران");
                    busy = false;
                    render();
                    if (manual) toast("تم تحديث المعلومات");
                    String requested = getIntent().getStringExtra("selected_feed");
                    if (requested != null) {
                        getIntent().removeExtra("selected_feed");
                        for (FeedRepository.Feed f : feeds) if (requested.equals(f.id())) { details(f); break; }
                    }
                });
            } catch (Exception error) {
                handler.post(() -> {
                    busy = false;
                    String why = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    if (why.length() > 135) why = why.substring(0, 135);
                    status.setText(feeds.isEmpty() ? "تعذر قراءة الفيدات: " + why :
                            "تعذر التحديث الأخير • المعلومات السابقة محفوظة");
                    if (manual) toast("فشل التحديث: " + error.getMessage());
                    if (feeds.isEmpty()) showEmpty();
                });
            }
        });
    }

    private Set<String> favorites() {
        return new HashSet<>(preferences.getStringSet("favorites", Collections.emptySet()));
    }
    private boolean matches(FeedRepository.Feed f, Set<String> fav) {
        if ("ON".equals(filter) && !f.isLive()) return false;
        if ("422".equals(filter) && !f.is422()) return false;
        if ("FAV".equals(filter) && !fav.contains(f.id())) return false;
        if (!satelliteFilter.isEmpty() && !f.satellite.equals(satelliteFilter)) return false;
        if (!query.isEmpty()) {
            String searchable = (f.name + " " + f.satellite + " " + f.frequencyText()).toLowerCase(Locale.ROOT);
            if (!searchable.contains(query)) return false;
        }
        return true;
    }
    private void render() {
        if (cards == null) return;
        cards.removeAllViews();
        List<FeedRepository.Feed> selected = new ArrayList<>();
        Set<String> fav = favorites();
        for (FeedRepository.Feed f : feeds) if (matches(f, fav)) selected.add(f);
        if (selected.isEmpty()) {
            showEmpty();
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int col = metrics.widthPixels > dp(1160) ? 4 : (metrics.widthPixels > dp(750) ? 3 : (metrics.widthPixels > dp(470) ? 2 : 1));
        int width = (metrics.widthPixels - dp(44) - dp(10) * col) / col;
        cards.setColumnCount(col);
        int count = Math.min(selected.size(), displayLimit);
        for (int i = 0; i < count; i++) {
            FeedRepository.Feed feed = selected.get(i);
            View card = makeCard(feed, width, fav.contains(feed.id()));
            GridLayout.LayoutParams g = new GridLayout.LayoutParams();
            g.width = width;
            g.height = -2;
            g.setMargins(dp(5), dp(5), dp(5), dp(6));
            cards.addView(card, g);
        }
        if (selected.size() > count) {
            TextView more = button("عرض المزيد  +" + Math.min(72, selected.size() - count), () -> {
                displayLimit += 72;
                render();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = width;
            lp.height = dp(66);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            cards.addView(more, lp);
        }
    }
    private void showEmpty() {
        cards.removeAllViews();
        TextView label = text(feeds.isEmpty() ? "🛰 لا توجد بيانات حالياً • جرّب زر تحديث" :
                "لا توجد فيدات ضمن هذا الفلتر", 18, MUTED, true);
        label.setGravity(Gravity.CENTER);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = -1;
        lp.height = dp(180);
        cards.addView(label, lp);
    }
    private View makeCard(FeedRepository.Feed f, int width, boolean favorite) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(outline(PANEL, 0xff2b3b52));
        c.setPadding(dp(9), dp(9), dp(9), dp(11));
        focusable(c, PANEL);
        c.setOnClickListener(v -> details(f));

        FrameLayout artwork = new FrameLayout(this);
        artwork.setBackground(shape(0xff152d3e, 12));
        c.addView(artwork, new LinearLayout.LayoutParams(-1, dp(125)));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.addView(image, new FrameLayout.LayoutParams(-1, -1));
        if (f.imageUrl != null && !f.imageUrl.isEmpty()) {
            ImageLoader.load(this, image, f.imageUrl);
        } else {
            TextView missing = text("◉  SATELLITE FEED", 15, 0xff426f81, true);
            missing.setGravity(Gravity.CENTER);
            artwork.addView(missing, new FrameLayout.LayoutParams(-1, -1));
        }
        TextView on = text(f.isLive() ? "●  ON AIR" : (f.isReported() ? "◉  رُصد" : "○  OFF"), 11,
                f.isLive() ? 0xff0b2620 : LIGHT, true);
        on.setGravity(Gravity.CENTER);
        on.setPadding(dp(9), dp(3), dp(9), dp(3));
        on.setBackground(shape(f.isLive() ? ACCENT : 0xff34425b, 10));
        FrameLayout.LayoutParams onp = new FrameLayout.LayoutParams(-2, dp(26), Gravity.TOP | Gravity.LEFT);
        onp.setMargins(dp(7), dp(7), 0, 0);
        artwork.addView(on, onp);
        if (f.is422()) {
            TextView format = text("4:2:2", 11, 0xff30200a, true);
            format.setPadding(dp(9), 0, dp(9), 0);
            format.setGravity(Gravity.CENTER);
            format.setBackground(shape(WARNING, 10));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, dp(25), Gravity.RIGHT | Gravity.TOP);
            fp.setMargins(0, dp(7), dp(7), 0);
            artwork.addView(format, fp);
        }

        TextView name = text((favorite ? "★ " : "") + f.name, 16, LIGHT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(dp(4), dp(10), dp(4), 0);
        c.addView(name, new LinearLayout.LayoutParams(-1, dp(36)));
        TextView satellite = text("🛰 " + f.satellite, 12, MUTED, false);
        satellite.setSingleLine(true);
        satellite.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(satellite, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView frequency = text(f.frequencyText(), 18, ACCENT, true);
        frequency.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(frequency, new LinearLayout.LayoutParams(-1, dp(32)));
        String codec = f.codec;
        if (codec.length() > 49) codec = codec.substring(0, 49) + "…";
        TextView info = text(f.chroma + "  •  " + codec, 11, MUTED, false);
        info.setSingleLine(true);
        info.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(info, new LinearLayout.LayoutParams(-1, dp(23)));
        String source = f.sources == null || f.sources.isEmpty() ? "Live-Feed.net" : f.sources;
        TextView providers = text("🌐 " + source, 11, 0xff9bb9d2, false);
        providers.setSingleLine(true);
        providers.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(providers, new LinearLayout.LayoutParams(-1, dp(23)));
        return c;
    }

    private void chooseSatellite() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (FeedRepository.Feed f : feeds) set.add(f.satellite);
        List<String> satList = new ArrayList<>(set);
        Collections.sort(satList);
        satList.add(0, "كل الأقمار");
        new AlertDialog.Builder(this).setTitle("اختيار القمر")
                .setItems(satList.toArray(new String[0]), (d, which) -> {
                    satelliteFilter = which == 0 ? "" : satList.get(which);
                    displayLimit = 72;
                    render();
                }).show();
    }
    private void details(FeedRepository.Feed f) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(15), dp(22), dp(19));
        panel.setBackground(shape(0xff142239, 20));
        TextView title = text(f.name, 22, LIGHT, true);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        if (f.imageUrl != null && !f.imageUrl.isEmpty()) {
            ImageView snapshot = new ImageView(this);
            snapshot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            panel.addView(snapshot, new LinearLayout.LayoutParams(-1, dp(190)));
            ImageLoader.load(this, snapshot, f.imageUrl);
        }
        line(panel, "الحالة", f.isLive() ? "🟢 مباشر" :
                (f.isReported() ? "بلاغ من هواة التقاط الفيدات (غير مؤكد حالياً)" : f.status));
        line(panel, "المصادر", f.sources == null ? "غير معروف" : f.sources);
        line(panel, "القمر", f.satellite);
        line(panel, "التردد / الاستقطاب / SR", f.frequencyText());
        line(panel, "الفيديو", f.codec);
        line(panel, "نوع البث", f.chroma);
        if (f.bitrate != null && !f.bitrate.isEmpty()) line(panel, "Bitrate", f.bitrate + " Mb/s");
        if (f.updated != null && !f.updated.isEmpty()) line(panel, "آخر رصد", f.updated);
        LinearLayout actions = new LinearLayout(this);
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(57)));
        TextView copy = button("نسخ التردد", () -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("DVB transponder", f.summary()));
            toast("تم نسخ معلومات الفيد");
        });
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(49), 1));
        TextView fav = button(favorites().contains(f.id()) ? "★ إزالة" : "☆ مفضلة", () -> {
            Set<String> set = favorites();
            if (!set.add(f.id())) set.remove(f.id());
            preferences.edit().putStringSet("favorites", set).apply();
            dialog.dismiss();
            render();
        });
        actions.addView(fav, new LinearLayout.LayoutParams(0, dp(49), 1));
        TextView launch = button("فتح IRAQhub", () -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("DVB TP", f.frequencyText()));
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.smarthub.plus");
            if (launchIntent != null) { startActivity(launchIntent); dialog.dismiss(); }
            else toast("IRAQhub غير مثبت أو غير ظاهر للنظام");
        });
        panel.addView(launch, new LinearLayout.LayoutParams(-1, dp(47)));
        TextView source = button("عرض المصدر الأصلي", () -> openSite(
                f.sourceUrl == null ? FeedRepository.SITE : f.sourceUrl));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(45));
        sp.topMargin = dp(7);
        panel.addView(source, sp);
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screenWidth - dp(25), dp(630)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    private void line(LinearLayout panel, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(row, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView name = text(label, 13, MUTED, false);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 0.42f));
        TextView details = text(value, 14, LIGHT, true);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(details, new LinearLayout.LayoutParams(0, -1, 0.58f));
    }
}
