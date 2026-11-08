package com.gazal.audiosync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** BEN10-style TV control panel, independent of protected BEN10 core.b10. */
public final class Ben10ConsoleActivity extends Activity {
    private static final int PICK = 501;
    private static final int NAVY = 0xEF071A2E;
    private static final int TILE = 0xDD17334E;
    private static final int CYAN = 0xFF1B9DD5;
    private static final int GREEN = 0xFF13B18A;
    private static final int WHITE = 0xFFE9F7FF;
    private final ArrayList<PlaylistStore.Source> sources = new ArrayList<>();
    private LinearLayout panel, sourceColumn, rightColumn;
    private TextView headline, state, sourceName, next, clock, info, count;
    private int selected = 0, page = 0, theme = 0, opacity = 94, scale = 100;
    private int videoDelayMs = 0, audioDelayMs = 0;
    private String playlistName = "My Sports Playlist";
    private final android.os.Handler tick = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timer = new Runnable() { @Override public void run() {
        if (clock != null) clock.setText(new SimpleDateFormat("hh:mm:ss a", Locale.US).format(new Date()));
        if (state != null) state.setText(AudioService.LAST_STATE + " • " + AudioService.LAST_STATION);
        tick.postDelayed(this, 1000);
    }};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        sources.addAll(PlaylistStore.load(this));
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        selected = p.getInt("selected", 0);
        page = selected / 6;
        theme = p.getInt("theme", 0);
        opacity = p.getInt("opacity", 94);
        scale = p.getInt("size", 100);
        videoDelayMs = p.getInt("videoDelay", 0);
        audioDelayMs = p.getInt("audioDelay", 0);
        playlistName = p.getString("playlist", "My Sports Playlist");
        draw();
    }
    @Override protected void onResume() { super.onResume(); tick.post(timer); }
    @Override protected void onPause() { tick.removeCallbacks(timer); super.onPause(); }
    private void persist() {
        getPreferences(MODE_PRIVATE).edit().putInt("selected", selected)
            .putInt("theme",theme).putInt("opacity",opacity).putInt("size",scale)
            .putInt("videoDelay",videoDelayMs).putInt("audioDelay",audioDelayMs)
            .putString("playlist",playlistName).apply();
    }
    private int background() { return theme == 1 ? 0xFF172935 : theme == 2 ? 0xFF261A41 : NAVY; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(9, 8, 9, 8);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button btn(String value, int tint, View.OnClickListener click) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(12);
        b.setTextColor(WHITE); b.setPadding(5,3,5,3);
        b.setBackgroundTintList(ColorStateList.valueOf(tint));
        b.setOnClickListener(click); b.setFocusable(true);
        return b;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void addFlexible(LinearLayout row, View child, int weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(3,3,3,3); row.addView(child, lp);
    }
    private void draw() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(0xCF000000);
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(14,10,14,10); panel.setBackgroundColor(background());
        panel.setAlpha(opacity/100f);
        panel.setScaleX(scale/100f); panel.setScaleY(scale/100f);
        FrameLayout.LayoutParams wrap = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        wrap.setMargins(9, 7, 9, 7);
        outer.addView(panel,wrap);

        LinearLayout header = row();
        TextView brand = text("BEN10 STYLE\nAUTO TIME SHIFT\nGAZAL • INDEPENDENT", 14, WHITE, true);
        addFlexible(header,brand,2);
        addFlexible(header,btn("◀ " + playlistName + " ▶", TILE, v -> renamePlaylist()),4);
        addFlexible(header,btn("OPACITY\n" + opacity + "%", TILE, v -> { opacity = opacity>=100 ? 70 : opacity+6; persist(); draw(); }),1);
        addFlexible(header,btn("SIZE\n" + scale + "%", TILE, v -> { scale = scale>=110 ? 80 : scale+10; persist(); draw(); }),1);
        addFlexible(header,btn("THEME\n" + (theme+1) + "/3", TILE, v -> { theme=(theme+1)%3; persist(); draw(); }),1);
        clock = text("",19,WHITE,true); addFlexible(header,clock,2);
        panel.addView(header);

        LinearLayout actions = row();
        addFlexible(actions,btn("▣ Import M3U",0xFF174F80,v -> importFile()),1);
        addFlexible(actions,btn("◇ Import CFG",0xFF174F80,v -> importFile()),1);
        addFlexible(actions,btn("⛓ Import M3U8",0xFF174F80,v -> importFile()),1);
        addFlexible(actions,btn("＋ Add Source",0xFF174F80,v -> addSource()),1);
        panel.addView(actions);

        state = text("READY",12,GREEN,true); state.setBackgroundColor(0xFF0A503F);
        panel.addView(state);

        LinearLayout body = row();
        sourceColumn = new LinearLayout(this); sourceColumn.setOrientation(LinearLayout.VERTICAL);
        sourceColumn.setPadding(4,8,6,8); sourceColumn.setBackgroundColor(TILE);
        count = text("AUDIO SOURCES • " + sources.size(),13,0xFF60D8FF,true);
        sourceColumn.addView(count);
        body.addView(sourceColumn,new LinearLayout.LayoutParams(0,0,4));
        rightColumn = new LinearLayout(this); rightColumn.setOrientation(LinearLayout.VERTICAL);
        rightColumn.setPadding(8,6,8,6); rightColumn.setBackgroundColor(TILE);
        body.addView(rightColumn,new LinearLayout.LayoutParams(0,0,6));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1,0,1);
        bodyParams.topMargin = 7; panel.addView(body, bodyParams);
        fillSources(); fillRight();

        LinearLayout shortcuts = row();
        addFlexible(shortcuts,btn("RED • Close",0xFF933142,v -> finish()),1);
        addFlexible(shortcuts,btn("GREEN • Restore DVB",0xFF126E56,v -> {
            Intent s = new Intent(this,AudioService.class).setAction(AudioService.STOP);
            startService(s);
        }),1);
        addFlexible(shortcuts,btn("YELLOW • Delay −0.5s",0xFF86721A,v -> adjustAudio(-500)),2);
        addFlexible(shortcuts,btn("BLUE • Delay +0.5s",0xFF1A659A,v -> adjustAudio(500)),2);
        addFlexible(shortcuts,btn("DVB Session",0xFF436784,v -> startActivity(new Intent(this,DvbSessionActivity.class))),1);
        addFlexible(shortcuts,btn("Advanced",0xFF436784,v -> startActivity(new Intent(this,MainActivity.class))),1);
        panel.addView(shortcuts);
        setContentView(outer);
    }
    private void fillSources() {
        sourceColumn.removeViews(1,sourceColumn.getChildCount()-1);
        int total = sources.size(), pages = Math.max(1,(total+5)/6);
        if (page>=pages) page=pages-1;
        for (int i=page*6;i<Math.min(total,(page+1)*6);i++) {
            final int index = i;
            PlaylistStore.Source src = sources.get(i);
            String name = src.name.length()>52 ? src.name.substring(0,52)+"…" : src.name;
            Button entry=btn((selected==i ? "●  ":"○  ")+name,
                selected==i ? 0xFF12788C : 0xFF124061, v -> choose(index));
            entry.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,0,1);
            lp.setMargins(2,3,2,3); sourceColumn.addView(entry,lp);
        }
        if (total==0) sourceColumn.addView(text("No sources imported\nاستخدم Import M3U أو Add Source",13,WHITE,false));
        LinearLayout footer = row();
        addFlexible(footer,btn("◀",0xFF285779,v -> {page=Math.max(0,page-1);fillSources();}),1);
        addFlexible(footer,text("PAGE "+(page+1)+"/"+pages+" • "+total+" sources",11,WHITE,false),3);
        addFlexible(footer,btn("▶",0xFF285779,v -> {page=Math.min(pages-1,page+1);fillSources();}),1);
        sourceColumn.addView(footer);
    }
    private void fillRight() {
        rightColumn.removeAllViews();
        PlaylistStore.Source s=sources.isEmpty()?null:sources.get(Math.min(selected,sources.size()-1));
        sourceName=text(s==null?"NO SOURCE":s.name,23,WHITE,true);
        rightColumn.addView(sourceName);
        headline=text(s==null?"Choose Import or Add Source":"EXTERNAL AUDIO • select source to play",12,GREEN,true);
        rightColumn.addView(headline);
        rightColumn.addView(text("NOW • Current programme unavailable",13,WHITE,true));
        next=text("NEXT • EPG requires channel mapping / XMLTV",12,0xFFB7CBDC,false);
        rightColumn.addView(next);
        rightColumn.addView(text("DVB PCM • Requires tuner capture bridge\nEXTERNAL PCM • "+AudioService.LAST_STATE,12,0xFF71D6E9,false));
        info = text("AUTO SYNC: NOT VERIFIED\nGazal tuner delay must be tested via TV Input session.\nManual controls below are active.",12,0xFFFFC981,false);
        info.setBackgroundColor(0xFF173044);
        rightColumn.addView(info);
        LinearLayout delayRow=row();
        addFlexible(delayRow,btn("VIDEO DELAY\n" + videoDelayMs/1000f+" s",0xFF2B456B,v->editVideoDelay()),1);
        addFlexible(delayRow,btn("AUDIO START DELAY\n" + audioDelayMs/1000f+" s",0xFF2B456B,v->editAudioDelay()),1);
        rightColumn.addView(delayRow);
        LinearLayout controls=row();
        addFlexible(controls,btn("▶ PLAY",0xFF137C6A,v->playSelected()),1);
        addFlexible(controls,btn("⏯ PAUSE",0xFF28679A,v->send(AudioService.TOGGLE)),1);
        addFlexible(controls,btn("■ STOP",0xFF8A3640,v->send(AudioService.STOP)),1);
        addFlexible(controls,btn("↥ SHOW OVERLAY",0xFF345777,v->openOverlay()),1);
        rightColumn.addView(controls);
        rightColumn.addView(text("VIDEO = DVB current + real TimeShift only if OEM supports it.\nAudio sync is NOT auto-matched yet.",11,0xFFB6C5D9,false));
    }
    private void choose(int index) { selected=index; page=index/6; persist(); fillSources(); fillRight(); }
    private void adjustAudio(int delta) {
        audioDelayMs=Math.min(30000,Math.max(0,audioDelayMs+delta));persist();fillRight();
    }
    private void editVideoDelay() { inputNumber("VIDEO DELAY seconds",videoDelayMs/1000f,true); }
    private void editAudioDelay() { inputNumber("AUDIO START DELAY seconds",audioDelayMs/1000f,false); }
    private void inputNumber(String title,float current,boolean video) {
        EditText e=new EditText(this);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(String.valueOf(current));
        new AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("Apply",(d,w)->{
            try {
                int ms=Math.max(0,Math.min(120000,Math.round(Float.parseFloat(e.getText().toString())*1000)));
                if(video)videoDelayMs=ms;else audioDelayMs=ms;
                persist();fillRight();
            }catch(Exception ignored){}
        }).setNegativeButton("Cancel",null).show();
    }
    private void playSelected() {
        if(sources.isEmpty()) return;
        PlaylistStore.Source src=sources.get(Math.min(selected,sources.size()-1));
        Intent i = new Intent(this,AudioService.class).setAction(AudioService.PLAY);
        i.putExtra("name",src.name);i.putExtra("url",src.url);i.putExtra("delay_ms",audioDelayMs);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i);else startService(i);
        fillRight();
    }
    private void send(String action) {
        Intent i=new Intent(this,AudioService.class).setAction(action);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    }
    private void openOverlay() {
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("Overlay permission").setMessage("منح صلاحية الظهور فوق القنوات")
                .setPositiveButton("Settings",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))))
                .setNegativeButton("Cancel",null).show();return;
        }
        send(AudioService.OVERLAY);
    }
    private void importFile() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,PICK);
    }
    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req!=PICK || result!=RESULT_OK || data==null || data.getData()==null)return;
        try(InputStream in=getContentResolver().openInputStream(data.getData())) {
            if(in==null)throw new IllegalArgumentException("No input");
            ArrayList<PlaylistStore.Source> added=PlaylistStore.parse(in);
            sources.addAll(added);PlaylistStore.save(this,sources);draw();
            new AlertDialog.Builder(this).setMessage("Imported "+added.size()+" sources").setPositiveButton("OK",null).show();
        }catch(Exception ex){new AlertDialog.Builder(this).setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void addSource() {
        LinearLayout f=new LinearLayout(this);f.setPadding(16,10,16,10);f.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(this),url=new EditText(this);
        name.setHint("Source name");url.setHint("https://...");url.setSingleLine(true);
        f.addView(name);f.addView(url);
        new AlertDialog.Builder(this).setTitle("Add Audio Source").setView(f).setPositiveButton("Save",(d,w)->{
            String link=url.getText().toString().trim();
            if(!PlaylistStore.valid(link))return;
            sources.add(new PlaylistStore.Source(name.getText().toString().trim().isEmpty()?
                "Audio "+(sources.size()+1):name.getText().toString().trim(),link));
            PlaylistStore.save(this,sources);draw();
        }).setNegativeButton("Cancel",null).show();
    }
    private void renamePlaylist() {
        EditText name=new EditText(this);name.setText(playlistName);
        new AlertDialog.Builder(this).setTitle("Playlist name").setView(name)
            .setPositiveButton("OK",(d,w)->{playlistName=name.getText().toString();persist();draw();})
            .setNegativeButton("Cancel",null).show();
    }
    @Override public boolean onKeyDown(int key,KeyEvent e) {
        if(key==KeyEvent.KEYCODE_PAGE_DOWN){ page=Math.min(Math.max(0,(sources.size()-1)/6),page+1);fillSources();return true;}
        if(key==KeyEvent.KEYCODE_PAGE_UP){page=Math.max(0,page-1);fillSources();return true;}
        if(key==KeyEvent.KEYCODE_PROG_RED){finish();return true;}
        if(key==KeyEvent.KEYCODE_PROG_GREEN){send(AudioService.STOP);return true;}
        if(key==KeyEvent.KEYCODE_PROG_YELLOW){adjustAudio(-500);return true;}
        if(key==KeyEvent.KEYCODE_PROG_BLUE){adjustAudio(500);return true;}
        return super.onKeyDown(key,e);
    }
}
