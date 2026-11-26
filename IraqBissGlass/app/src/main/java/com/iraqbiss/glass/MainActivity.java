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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int CYAN=Color.rgb(179,205,255), WHITE=Color.WHITE, SOFT=Color.rgb(176,190,210);
    private final List<FeedData.Item> visible=new ArrayList<>();
    private TextView stats;
    private ImageLoader images;
    private FrameLayout hero;
    private ImageView heroImage;
    private TextView heroTitle,heroKicker,heroInfo;
    private FeedData.Item highlighted;
    private FeedAdapter adapter;
    private Button[] tabs;
    private int selected=0;
    private final BroadcastReceiver update=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){drawFeeds();}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        getWindow().setStatusBarColor(Color.rgb(7,20,36));
        images=new ImageLoader(this);
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
            new int[]{Color.rgb(6,11,21),Color.rgb(19,25,41),Color.rgb(9,11,23)});
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
        v.setBackground(background(Color.argb(128,53,62,91),Color.argb(148,23,29,49),
            Color.argb(128,148,170,215),22));
        v.setPadding(dp(18),dp(11),dp(18),dp(11));return v;
    }
    private LinearLayout.LayoutParams lp(int width,int height,int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));
        p.setMargins(dp(margin),0,dp(margin),0);return p;
    }
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(appBackground());root.setPadding(dp(30),dp(14),dp(30),dp(12));
        setContentView(root);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("IRAQBISS  ·  tv",26,WHITE,true);
        brand.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(55),1));
        header.addView(chip("⟳  تحديث",()->{stats.setText("جاري تحديث المصادر...");startWorker(true);}),lp(125,47,3));
        header.addView(chip("◫  المصادر",this::sources),lp(132,47,3));
        header.addView(chip("⚙  الإعدادات",this::settings),lp(145,47,3));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        hero=new FrameLayout(this);
        hero.setBackground(background(Color.rgb(56,67,100),Color.rgb(12,24,44),Color.argb(153,142,169,214),26));
        hero.setClipToOutline(true);
        heroImage=new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(heroImage,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.argb(239,8,13,28),Color.argb(188,11,18,32),Color.argb(62,10,18,34)}));
        hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(33),dp(19),dp(33),dp(20));info.setGravity(Gravity.BOTTOM|Gravity.LEFT);
        heroKicker=label("●  LIVE FEED RADAR  /  صورة المصدر عند توفرها",15,CYAN,true);
        heroTitle=label("استكشف فيدات الأقمار الصناعية",30,WHITE,true);
        heroInfo=label("انتقل بين البطاقات لاختيار الفيد وعرض التردد",17,SOFT,false);
        heroKicker.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        heroTitle.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        heroInfo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        info.addView(heroKicker,new LinearLayout.LayoutParams(-1,dp(35)));
        info.addView(heroTitle,new LinearLayout.LayoutParams(-1,dp(54)));
        info.addView(heroInfo,new LinearLayout.LayoutParams(-1,dp(38)));
        hero.addView(info,new FrameLayout.LayoutParams(-1,-1));
        hero.setFocusable(true);
        hero.setOnClickListener(v->{if(highlighted!=null)details(highlighted);});
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(218));
        hp.setMargins(dp(2),dp(2),dp(2),dp(10));root.addView(hero,hp);

        LinearLayout filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);
        String[] names={"الكل","الرياضة","4:2:2","مفتوحة FTA","المفضلة"};
        tabs=new Button[names.length];
        for(int j=0;j<names.length;j++){
            final int index=j;tabs[j]=chip(names[j],()->{selected=index;drawFeeds();});
            LinearLayout.LayoutParams p=lp(120,43,4);
            if(j==0)p.width=dp(100);
            if(j==3)p.width=dp(155);
            filters.addView(tabs[j],p);
        }
        root.addView(filters,new LinearLayout.LayoutParams(-1,dp(54)));

        stats=label("جاري قراءة البيانات...",14,SOFT,false);
        stats.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,dp(32));
        st.setMargins(dp(8),0,dp(8),dp(4));root.addView(stats,st);

        RecyclerView cards=new RecyclerView(this);
        float logicalWidth=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
        int cols=logicalWidth>1400?4:(logicalWidth>850?3:2);
        cards.setLayoutManager(new GridLayoutManager(this,cols));
        cards.setItemViewCacheSize(12);
        cards.setPadding(dp(1),dp(3),dp(1),dp(3));cards.setClipToPadding(false);
        adapter=new FeedAdapter();cards.setAdapter(adapter);
        root.addView(cards,new LinearLayout.LayoutParams(-1,0,1));

        TextView footer=label("OK  تفاصيل الفيد       ◦       ♥  المفضلة من التفاصيل       ◦       MENU  الإعدادات       ◦       صور أصلية عند توفرها",13,SOFT,false);
        footer.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(28)));
    }
    private void showHero(FeedData.Item f){
        highlighted=f;
        if(heroImage==null)return;
        if(f==null){
            images.into(heroImage,null);
            heroKicker.setText("●  IRAQBISS · SATELLITE FEED DISCOVERY");
            heroTitle.setText("لا توجد فيدات بعد");
            heroInfo.setText("اختر تحديث أو راجع حالة المصادر من الإعدادات");
            return;
        }
        images.into(heroImage,f.image);
        heroKicker.setText("●  "+f.source.toUpperCase(Locale.ROOT)+"     /     "+(f.fourTwoTwo()?"4:2:2":"SATELLITE FEED"));
        heroTitle.setText(f.title);
        heroInfo.setText("◉  "+f.sat+"       "+f.tp()+"       "+f.crypt);
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
        if(!visible.contains(highlighted))showHero(visible.isEmpty()?null:visible.get(0));
        else if(highlighted!=null)showHero(highlighted);
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
            final LinearLayout card;
            final TextView name,tp,meta,badge;
            final ImageView photo;
            Holder(LinearLayout v,TextView a,TextView b,TextView c,TextView d,ImageView image){
                super(v);card=v;name=a;tp=b;meta=c;badge=d;photo=image;
            }
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);card.setClickable(true);card.setClipToPadding(false);
            StateListDrawable state=new StateListDrawable();
            state.addState(new int[]{android.R.attr.state_focused},background(
                Color.rgb(69,81,119),Color.rgb(30,42,69),CYAN,22));
            state.addState(new int[]{},background(
                Color.argb(235,38,47,71),Color.argb(238,20,25,43),Color.argb(92,158,178,219),22));
            card.setBackground(state);
            card.setPadding(dp(7),dp(7),dp(7),dp(8));
            FrameLayout poster=new FrameLayout(MainActivity.this);
            poster.setBackground(background(Color.rgb(42,53,79),Color.rgb(13,25,44),Color.argb(85,124,154,197),15));
            poster.setClipToOutline(true);
            TextView fallback=label("◉  SATELLITE FEED",17,SOFT,true);
            fallback.setGravity(Gravity.CENTER);
            poster.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            ImageView image=new ImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView badge=label("  ◉  NEW FEED  ",12,WHITE,true);
            badge.setBackground(background(Color.argb(210,25,36,63),Color.argb(210,17,31,54),Color.argb(185,200,214,255),12));
            badge.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(122),dp(27),Gravity.TOP|Gravity.RIGHT);
            bp.setMargins(0,dp(10),dp(12),0);poster.addView(badge,bp);
            card.addView(poster,new LinearLayout.LayoutParams(-1,dp(135)));
            TextView title=label("",19,WHITE,true);
            TextView freq=label("",16,CYAN,true);
            TextView meta=label("",13,SOFT,false);
            card.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
            card.addView(freq,new LinearLayout.LayoutParams(-1,dp(29)));
            card.addView(meta,new LinearLayout.LayoutParams(-1,dp(24)));
            RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,dp(242));
            params.setMargins(dp(6),dp(3),dp(6),dp(11));card.setLayoutParams(params);
            card.setOnFocusChangeListener((view,focused)->{
                view.animate().scaleX(focused?1.035f:1f).scaleY(focused?1.035f:1f)
                    .setDuration(160).start();
                view.setElevation(focused?dp(14):0);
                if(focused){
                    int position=((RecyclerView.ViewHolder)card.getTag()).getBindingAdapterPosition();
                    if(position>=0 && position<visible.size())showHero(visible.get(position));
                }
            });
            Holder h=new Holder(card,title,freq,meta,badge,image);
            card.setTag(h);
            return h;
        }
        @Override public void onBindViewHolder(Holder h,int position){
            FeedData.Item f=visible.get(position);
            h.name.setText((FeedData.favorite(MainActivity.this,f)?"★  ":"")+f.title);
            h.tp.setText("◉  "+f.sat+"       "+f.tp());
            h.meta.setText(f.source+"    ·    "+f.crypt+"    ·    "+(f.fourTwoTwo()?"4:2:2":(f.sport()?"رياضة":"فيد")));
            h.badge.setText(f.fourTwoTwo()?"  4:2:2  ":f.crypt.equals("FTA")?"  FTA  ":"  SAT FEED  ");
            images.into(h.photo,f.image);
            h.card.setOnClickListener(v->details(f));
            h.card.setOnLongClickListener(v->{
                if(f.link.startsWith("https://"))try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(f.link)));}
                catch(Exception ignored){}
                return true;
            });
        }
        @Override public void onViewRecycled(Holder h){
            super.onViewRecycled(h);
            h.photo.setTag(null);
            h.photo.setImageDrawable(null);
        }
        @Override public int getItemCount(){return visible.size();}
    }
}
