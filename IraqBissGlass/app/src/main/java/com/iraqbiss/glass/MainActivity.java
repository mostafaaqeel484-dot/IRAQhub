package com.iraqbiss.glass;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int CYAN=Color.rgb(81,232,210), WHITE=Color.WHITE, SOFT=Color.rgb(185,210,230);
    private final List<FeedData.Item> visible=new ArrayList<>();
    private TextView stats;
    private FeedAdapter adapter;
    private Button[] tabs;
    private int selected=0;
    private final BroadcastReceiver update=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){drawFeeds();}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        getWindow().setStatusBarColor(Color.rgb(7,20,36));
        buildUi();drawFeeds();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},340);
        startWorker(false);
    }
    @Override public void onStart(){
        super.onStart();
        IntentFilter filter=new IntentFilter(FeedService.UPDATED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(update,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(update,filter);
        drawFeeds();
    }
    @Override public void onStop(){unregisterReceiver(update);super.onStop();}
    private void startWorker(boolean manual){
        Intent i=new Intent(this,FeedService.class);
        if(manual)i.setAction(FeedService.REFRESH);
        try{startForegroundService(i);}catch(Exception ex){toast("تعذّر بدء المراقبة: "+ex.getMessage());}
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int first,int second,int stroke,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});
        g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;
    }
    private GradientDrawable appBackground(){
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(6,18,36),Color.rgb(14,35,58),Color.rgb(8,17,31)});
    }
    private TextView label(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(sp);
        t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button chip(String value,Runnable click){
        Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(16);
        b.setTextColor(WHITE);b.setFocusable(true);b.setPadding(dp(8),0,dp(8),0);
        StateListDrawable state=new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_focused},
            background(Color.argb(232,30,74,95),Color.argb(240,14,45,67),CYAN,16));
        state.addState(new int[]{},
            background(Color.argb(125,77,116,155),Color.argb(142,19,42,64),Color.argb(130,150,190,221),16));
        b.setBackground(state);b.setOnClickListener(v->click.run());return b;
    }
    private LinearLayout glassPanel(){
        LinearLayout v=new LinearLayout(this);
        v.setBackground(background(Color.argb(130,55,98,132),Color.argb(142,16,33,58),
            Color.argb(150,116,166,206),22));
        v.setPadding(dp(18),dp(11),dp(18),dp(11));return v;
    }
    private LinearLayout.LayoutParams lp(int width,int height,int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));
        p.setMargins(dp(margin),0,dp(margin),0);return p;
    }
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(appBackground());root.setPadding(dp(30),dp(18),dp(30),dp(14));
        setContentView(root);
        LinearLayout header=glassPanel();header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("◉   IRAQBISS  /  GLASS FEEDS",27,CYAN,true);
        brand.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);header.addView(brand,new LinearLayout.LayoutParams(0,dp(53),1));
        header.addView(chip("⟳  تحديث",()->{stats.setText("جاري تحديث المصادر...");startWorker(true);}),lp(135,49,4));
        header.addView(chip("☷  المصادر",this::sources),lp(140,49,4));
        header.addView(chip("⚙  الإعدادات",this::settings),lp(160,49,4));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(76)));
        stats=label("تحميل آخر البيانات...",16,SOFT,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(47));sp.setMargins(dp(10),dp(3),dp(6),0);
        root.addView(stats,sp);
        LinearLayout filterPanel=glassPanel();filterPanel.setPadding(dp(8),dp(4),dp(8),dp(4));
        String[] names={"كل الفيدات","رياضة","4:2:2","FTA","المفضلة"};
        tabs=new Button[names.length];
        for(int j=0;j<names.length;j++){
            final int index=j;tabs[j]=chip(names[j],()->{selected=index;drawFeeds();});
            filterPanel.addView(tabs[j],new LinearLayout.LayoutParams(0,dp(43),1));
        }
        root.addView(filterPanel,new LinearLayout.LayoutParams(-1,dp(54)));
        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));list.setItemViewCacheSize(10);
        list.setPadding(0,dp(6),0,dp(6));list.setClipToPadding(false);
        adapter=new FeedAdapter();list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=label("OK  التفاصيل    •    القائمة  الإعدادات    •    ★  المفضلة    •    تنبيهات Glass فوق القنوات عند السماح",14,SOFT,false);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(28)));
    }
    private void drawFeeds(){
        if(adapter==null)return;
        List<FeedData.Item> all=FeedData.cached(this);visible.clear();
        for(FeedData.Item f:all){
            if(selected==1 && !f.sport())continue;
            if(selected==2 && !f.fourTwoTwo())continue;
            if(selected==3 && !f.crypt.equals("FTA"))continue;
            if(selected==4 && !FeedData.favorite(this,f))continue;
            visible.add(f);
        }
        adapter.notifyDataSetChanged();
        long at=FeedData.lastSync(this);
        String when=at==0?"لم يتم التحديث بعد":new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(at));
        stats.setText("📡  "+all.size()+" فيد مسجّل     ◦     آخر محاولة: "+when+"     ◦     بلاغات مصادر، وليست قياس إشارة DVB");
        for(int i=0;i<tabs.length;i++) tabs[i].setTextColor(i==selected?CYAN:WHITE);
    }
    private AlertDialog decorate(AlertDialog dialog){
        dialog.setOnShowListener(v->{
            Window window=dialog.getWindow();
            if(window!=null)window.setBackgroundDrawable(background(Color.rgb(40,70,100),Color.rgb(13,30,55),CYAN,25));
            if(dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(CYAN);
            if(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SOFT);
            if(dialog.getButton(AlertDialog.BUTTON_NEUTRAL)!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(CYAN);
        });
        dialog.show();return dialog;
    }
    private void details(FeedData.Item f){
        String data="📡 "+f.sat+"  ·  "+f.tp()+
           "\n\n🎬 الفيديو: "+(f.video.isEmpty()?"غير معروف":f.video)+
           "\n🔐 الحالة: "+f.crypt+
           "\n🌐 المصدر: "+f.source+
           "\n\nيمكنك نسخ بيانات التردد لإدخالها في بحث الستلايت يدوياً. عرض الفيد هنا لا يعني أنه يبث حالياً.";
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(f.title).setMessage(data)
           .setPositiveButton("نسخ التردد",(d,n)->{
               ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TP",f.sat+" "+f.tp()));
               toast("تم نسخ بيانات التردد");
           })
           .setNeutralButton(FeedData.favorite(this,f)?"★ إزالة المفضلة":"☆ إضافة للمفضلة",(d,n)->{
               FeedData.flipFavorite(this,f);drawFeeds();
           })
           .setNegativeButton("إغلاق",null).create();
        decorate(dialog);
    }
    private void settings(){
        SharedPreferences p=getSharedPreferences("iraqbiss_settings",MODE_PRIVATE);
        String[] options={
          (p.getBoolean("overlay",false)?"إيقاف":"تفعيل")+" نافذة Glass فوق القنوات",
          "إضافة مصادر RSS/Atom",
          "حالة اتصال المواقع",
          "تحديث الفيدات الآن",
          "إيقاف خدمة الخلفية"
        };
        decorate(new AlertDialog.Builder(this).setTitle("إعدادات IraqBISS Glass").setItems(options,(d,n)->{
            switch(n){
                case 0:overlay();break;
                case 1:rss();break;
                case 2:decorate(new AlertDialog.Builder(this).setTitle("تشخيص المصادر").setMessage(FeedData.statuses(this)).setPositiveButton("حسناً",null).create());break;
                case 3:startWorker(true);break;
                case 4:stopService(new Intent(this,FeedService.class));toast("تم إيقاف المراقبة");break;
            }
        }).setNegativeButton("إغلاق",null).create());
    }
    private void sources(){
        String[][] builtin=FeedData.BUILTIN;
        SharedPreferences p=getSharedPreferences("iraqbiss_settings",MODE_PRIVATE);
        boolean[] selected=new boolean[builtin.length];String[] labels=new String[builtin.length];
        for(int j=0;j<builtin.length;j++){
            selected[j]=p.getBoolean("source_"+builtin[j][0],true);
            labels[j]=builtin[j][0]+"   ·   "+builtin[j][1];
        }
        decorate(new AlertDialog.Builder(this).setTitle("مصادر الفيدات")
          .setMultiChoiceItems(labels,selected,(d,n,checked)->selected[n]=checked)
          .setPositiveButton("حفظ وتحديث",(d,n)->{
              SharedPreferences.Editor e=p.edit();
              for(int j=0;j<builtin.length;j++)e.putBoolean("source_"+builtin[j][0],selected[j]);
              e.apply();startWorker(true);
          }).setNegativeButton("إلغاء",null).create());
    }
    private void rss(){
        EditText field=new EditText(this);field.setSingleLine(false);field.setMinLines(5);
        field.setTextSize(16);field.setTextColor(WHITE);field.setHintTextColor(SOFT);
        field.setHint("https://example.org/feed.xml");
        SharedPreferences p=getSharedPreferences("iraqbiss_settings",MODE_PRIVATE);
        field.setText(p.getString("rss",""));
        decorate(new AlertDialog.Builder(this).setTitle("روابط RSS (واحد في كل سطر)")
          .setMessage("حتى 15 مصدراً HTTPS، ويجب أن تتضمن المنشورات التردد والاستقطاب والترميز.")
          .setView(field).setPositiveButton("حفظ",(d,n)->{
              p.edit().putString("rss",field.getText().toString()).apply();startWorker(true);
          }).setNegativeButton("إلغاء",null).create());
    }
    private void overlay(){
        SharedPreferences p=getSharedPreferences("iraqbiss_settings",MODE_PRIVATE);
        boolean enabled=!p.getBoolean("overlay",false);
        p.edit().putBoolean("overlay",enabled).apply();
        if(enabled && !Settings.canDrawOverlays(this)){
            decorate(new AlertDialog.Builder(this).setTitle("إذن النوافذ العائمة")
                .setMessage("اسمح لتطبيق IraqBISS Glass بالظهور فوق التطبيقات حتى تُعرض تنبيهات الفيدات فوق بث الستلايت. بعض أنظمة الأجهزة تمنع ذلك.")
                .setPositiveButton("فتح الإذن",(d,n)->{
                    try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
                    catch(Exception e){toast("افتح الإذن من إعدادات التطبيقات");}
                }).setNegativeButton("إلغاء",(d,n)->p.edit().putBoolean("overlay",false).apply()).create());
        } else toast(enabled?"تنبيهات الشاشة مفعلة":"تم تعطيل تنبيهات الشاشة");
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override public boolean onKeyDown(int key,KeyEvent e){
        if(key==KeyEvent.KEYCODE_MENU){settings();return true;}
        return super.onKeyDown(key,e);
    }
    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.Holder>{
        class Holder extends RecyclerView.ViewHolder {
            final LinearLayout card;final TextView name,tp,meta;
            Holder(LinearLayout v,TextView a,TextView b,TextView c){super(v);card=v;name=a;tp=b;meta=c;}
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(24),dp(10),dp(24),dp(10));card.setFocusable(true);card.setClickable(true);
            StateListDrawable focus=new StateListDrawable();
            focus.addState(new int[]{android.R.attr.state_focused},background(
                Color.argb(234,46,94,125),Color.argb(239,20,53,82),CYAN,20));
            focus.addState(new int[]{},background(
                Color.argb(139,83,131,169),Color.argb(162,20,46,73),Color.argb(129,129,174,218),20));
            card.setBackground(focus);
            TextView title=label("",21,WHITE,true);
            TextView tp=label("",19,CYAN,true);
            TextView meta=label("",14,SOFT,false);
            card.addView(title,new LinearLayout.LayoutParams(-1,dp(33)));
            card.addView(tp,new LinearLayout.LayoutParams(-1,dp(30)));
            card.addView(meta,new LinearLayout.LayoutParams(-1,dp(25)));
            RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,dp(105));
            params.setMargins(dp(3),0,dp(3),dp(9));card.setLayoutParams(params);
            return new Holder(card,title,tp,meta);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            FeedData.Item f=visible.get(position);
            h.name.setText((FeedData.favorite(MainActivity.this,f)?"★ ":"")+" "+f.title);
            h.tp.setText("🛰 "+f.sat+"        "+f.tp()+(f.fourTwoTwo()?"     ◉ 4:2:2":""));
            h.meta.setText("◦ "+f.source+"       ◦ "+f.crypt+"       ◦ "+(f.sport()?"رياضة":"فيد عام")+"       ◦  بلاغ تردد");
            h.card.setOnClickListener(v->details(f));
            h.card.setOnLongClickListener(v->{
                if(f.link.startsWith("https://"))try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(f.link)));}
                catch(Exception ignored){}
                return true;
            });
        }
        @Override public int getItemCount(){return visible.size();}
    }
}
