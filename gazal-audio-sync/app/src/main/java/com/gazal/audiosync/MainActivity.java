package com.gazal.audiosync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 101;
    private static final int BACKGROUND = 0xFF071324;
    private static final int CARD = 0xFF102943;
    private static final int BLUE = 0xFF1766A0;
    private static final int GREEN = 0xFF168568;
    private static final int WHITE = 0xFFEBF6FF;
    private final ArrayList<PlaylistStore.Source> sources = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView status, delayLabel, totalLabel;
    private int startDelayMs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        sources.addAll(PlaylistStore.load(this));
        startDelayMs = getPreferences(MODE_PRIVATE).getInt("startDelayMs", 0);
        render();
    }

    private TextView text(String label, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(label); t.setTextColor(WHITE); t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(12, 8, 12, 8);
        return t;
    }
    private Button button(String label, int tint, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTextSize(14); b.setBackgroundTintList(ColorStateList.valueOf(tint));
        b.setOnClickListener(action);
        b.setFocusable(true);
        return b;
    }
    private void rowAdd(LinearLayout row, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(4, 4, 4, 4);
        row.addView(b, p);
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }
    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(18, 12, 18, 12);

        LinearLayout titleRow = row();
        TextView title = text("GAZAL  /  AUDIO SYNC", 25, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,-2,1));
        TextView subtitle = text("Android 14 • DVB / External Audio", 13, false);
        titleRow.addView(subtitle);
        root.addView(titleRow);

        TextView warning = text("مصادر صوت مستقلة + واجهة عائمة | تأخير فيديو DVB غير مدعوم حالياً", 13, false);
        warning.setTextColor(0xFFFFD27C);
        warning.setBackgroundColor(CARD);
        root.addView(warning);

        LinearLayout top = row();
        rowAdd(top, button("استيراد M3U / CFG", BLUE, v -> importSources()));
        rowAdd(top, button("إضافة رابط صوت", BLUE, v -> addLink()));
        rowAdd(top, button("إظهار فوق القناة", GREEN, v -> showOverlay()));
        rowAdd(top, button("فحص DVB و ADB", BLUE, v -> diagnose()));
        root.addView(top);

        LinearLayout banner = row();
        totalLabel = text("", 16, true);
        banner.addView(totalLabel, new LinearLayout.LayoutParams(0, -2, 1));
        delayLabel = text("", 15, false);
        banner.addView(delayLabel);
        root.addView(banner);

        ListView sourceList = new ListView(this);
        sourceList.setBackgroundColor(CARD);
        sourceList.setDividerHeight(2);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        sourceList.setAdapter(adapter);
        sourceList.setOnItemClickListener((p,v,index,id) -> play(index));
        root.addView(sourceList, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout controls = row();
        rowAdd(controls, button("بدء لاحقاً −0.5ث", BLUE, v -> changeDelay(-500)));
        rowAdd(controls, button("بدء لاحقاً +0.5ث", BLUE, v -> changeDelay(+500)));
        rowAdd(controls, button("إيقاف الصوت", 0xFF9B313A, v -> audioAction(AudioService.STOP)));
        rowAdd(controls, button("إيقاف/استئناف", GREEN, v -> audioAction(AudioService.TOGGLE)));
        root.addView(controls);

        status = text("جاهز • اختر مصدر صوت من القائمة", 14, false);
        status.setBackgroundColor(CARD);
        root.addView(status);
        TextView footer = text("مهم: Start delay تأخير بدء فقط، وليس مزامنة PCM أو تغيير وقت بث القناة.", 12, false);
        footer.setTextColor(0xFFACBBCD);
        root.addView(footer);
        setContentView(root);
        refreshSources();
        updateDelay();
    }
    private void refreshSources() {
        if (adapter == null) return;
        adapter.clear();
        for (PlaylistStore.Source s : sources) adapter.add(s.name + "\n" + s.url);
        adapter.notifyDataSetChanged();
        totalLabel.setText("مصادر الصوت: " + sources.size() + "  •  اضغط OK للتشغيل");
    }
    private void updateDelay() {
        if (delayLabel != null) delayLabel.setText("تأخير بدء الصوت: " + (startDelayMs / 1000f) + " ث");
    }
    private void changeDelay(int delta) {
        startDelayMs = Math.max(0, Math.min(30000, startDelayMs + delta));
        getPreferences(MODE_PRIVATE).edit().putInt("startDelayMs", startDelayMs).apply();
        updateDelay();
    }
    private void play(int index) {
        if (index < 0 || index >= sources.size()) return;
        PlaylistStore.Source s = sources.get(index);
        Intent i = new Intent(this, AudioService.class);
        i.setAction(AudioService.PLAY);
        i.putExtra("name", s.name);
        i.putExtra("url", s.url);
        i.putExtra("delay_ms", startDelayMs);
        startServiceSafely(i);
        status.setText("طلب تشغيل: " + s.name + " • راقب إشعار الصوت لمعرفة حالة الاتصال");
    }
    private void audioAction(String action) {
        Intent i = new Intent(this, AudioService.class);
        i.setAction(action);
        startServiceSafely(i);
        status.setText(action.equals(AudioService.STOP) ? "تم طلب إيقاف الصوت" : "تم تغيير حالة المشغل");
    }
    private void startServiceSafely(Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setMessage("تعذر تشغيل الخدمة: " + e).setPositiveButton("OK", null).show();
        }
    }
    private void showOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("صلاحية الظهور فوق القنوات")
                .setMessage("اسمح للتطبيق بالظهور فوق التطبيقات من إعدادات Android. الموافقة بيد المستخدم.")
                .setPositiveButton("فتح الإعدادات", (d,w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("إلغاء", null).show();
            return;
        }
        Intent i = new Intent(this, AudioService.class);
        i.setAction(AudioService.OVERLAY);
        startServiceSafely(i);
        status.setText("تم طلب إظهار واجهة الصوت فوق القناة");
    }
    private void importSources() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, PICK_FILE); }
        catch (Exception e) { alert("مدير الملفات غير متوفر: " + e.getMessage()); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            if (in == null) throw new Exception("تعذر قراءة الملف");
            ArrayList<PlaylistStore.Source> imported = PlaylistStore.parse(in);
            sources.addAll(imported);
            PlaylistStore.save(this, sources);
            refreshSources();
            alert("تم استيراد " + imported.size() + " مصدر. روابط HLS أو قوائم DRM قد لا تعمل.");
        } catch (Exception e) { alert("خطأ بالاستيراد: " + e.getMessage()); }
    }
    private void addLink() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(20,10,20,10);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("اسم المصدر");
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("https://example.com/live.m3u8");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(name); form.addView(url);
        new AlertDialog.Builder(this).setTitle("إضافة مصدر صوت").setView(form)
            .setPositiveButton("حفظ", (d,w) -> {
                String u = url.getText().toString().trim();
                if (!PlaylistStore.valid(u)) { alert("الرابط يجب أن يبدأ بـ http:// أو https://"); return; }
                String n = name.getText().toString().trim();
                sources.add(new PlaylistStore.Source(n.isEmpty() ? "Audio " + (sources.size() + 1) : n,u));
                PlaylistStore.save(this,sources); refreshSources();
            }).setNegativeButton("إلغاء", null).show();
    }
    private void diagnose() {
        status.setText("جار فحص خدمات الجهاز (قراءة فقط)...");
        new Thread(() -> {
            String result = DvbProbe.snapshot();
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    status.setText("انتهى الفحص • المسارات والـ PID بالنافذة");
                    new AlertDialog.Builder(this).setTitle("Gazal DVB / ADB Diagnostic")
                        .setMessage(result).setPositiveButton("إغلاق", null).show();
                }
            });
        }).start();
    }
    private void alert(String msg) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show();
    }
}
