package com.smarthub.plus;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

/** Wallpapers are rendered locally, without network, video decoder or OEM data. */
public final class RxAppearanceActivity extends HubActivity {
 private static final int PICK_RX_APK=1829;
 @Override public void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=PICK_RX_APK||result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  new Thread(()->{
   try{
    int count=RxOriginalAssets.importFromApk(this,uri);
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     toast(tx("تم استيراد "+count+" صورة RX الأصلية","Imported "+count+" original RX images"));
     recreate();
    });
   }catch(Exception e){
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     new android.app.AlertDialog.Builder(this)
      .setTitle(tx("استيراد RX لم ينجح","RX import failed"))
      .setMessage(e.getMessage()==null?"RX_APK_IMPORT_FAILED":e.getMessage())
      .setPositiveButton("OK",null).show();
    });
   }
  },"iraqhub-rx-assets-import").start();
 }
 private void importRxOriginal(){
  Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  picker.addCategory(Intent.CATEGORY_OPENABLE);
  picker.setType("*/*");
  try{startActivityForResult(picker,PICK_RX_APK);}
  catch(Exception missing){toast(tx("منتقي الملفات غير متاح","Document picker unavailable"));}
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);render();
 }
 private void render(){
  LinearLayout body=page(tx("المظهر والخلفيات · RX Edition","RX Edition · appearance and wallpapers"),
    tx("اختيار خلفية واحدة يوحّد ألوان HOME والإنفوبار والقنوات والقوائم. لا يغير وظائف DVB.",
       "A wallpaper also sets the Home, InfoBar and channel-list accent. DVB functions stay untouched."));
  LinearLayout info=card(body);
  info.addView(text(tx("خلفيات مدمجة بدون اتصال أو فيديو 4K","Offline wallpapers · no 4K decoder load"),17,true));
  note(info,tx("المناظر رياضية/وطنية/تقنية مصممة بشكل رمزي، وليست شعارات أو صوراً رسمية للأندية. يحافظ الوضع الثابت على سلاسة 4:2:2.",
    "Original stylized backgrounds, not official club logos or licensed photos. Static images preserve 4:2:2 playback resources."));
  LinearLayout original=card(body);
  original.addView(text(tx("نقل صور RX Launcher الأصلية","Import original RX Launcher visuals"),19,true));
  note(original,tx("حدد ملف RX-Launcher-v2.39.apk الأصلي من الجهاز. يتم استخراج الخلفيات وبطاقات Live TV فقط بدون تشغيل كود RX أو العبث بالقنوات.",
    "Select your RX-Launcher-v2.39.apk. Extracts the original wallpapers and Live-TV images only; never runs RX code or alters channel functions."));
  action(original,RxOriginalAssets.imported(this)?
    tx("✓ صور RX الأصلية مستوردة · تحديث","✓ Original RX images imported · refresh"):
    tx("استيراد ملف RX APK الأصلي","Select original RX APK"),this::importRxOriginal);
  LinearLayout options=card(body);
  CheckBox auto=check(options,tx("تدوير الخلفيات تلقائياً كل يوم","Rotate wallpaper automatically each day"),
    prefs().getBoolean("rx_wallpaper_rotate",false));
  auto.setOnCheckedChangeListener((button,checked)->{
   prefs().edit().putBoolean("rx_wallpaper_rotate",checked).apply();
   recreate();
  });
  CheckBox glass=check(options,tx("تفعيل البطاقات الزجاجية الشفافة","Translucent glass cards"),
    prefs().getBoolean("rx_glass_effect",true));
  glass.setOnCheckedChangeListener((button,checked)->{
   prefs().edit().putBoolean("rx_glass_effect",checked).apply();
   recreate();
  });
  for(int row=0;row<RxSkin.IDS.length;row+=3){
   LinearLayout line=row();
   line.setGravity(Gravity.CENTER_VERTICAL);
   body.addView(line,new LinearLayout.LayoutParams(-1,dp(135)));
   for(int j=row;j<Math.min(row+3,RxSkin.IDS.length);j++){
    final int theme=j;
    FrameLayout item=new FrameLayout(this);
    item.setFocusable(true);item.setClickable(true);item.setDefaultFocusHighlightEnabled(false);
    item.setContentDescription(RxSkin.title(this,j));
    android.graphics.drawable.Drawable visual=theme==0?RxOriginalAssets.drawable(this,"wall_dark",600):
     theme==1?RxOriginalAssets.drawable(this,"wall_light",600):
     theme==5?RxOriginalAssets.drawable(this,"madrid",600):
     theme==6?RxOriginalAssets.drawable(this,"ahly",600):
     theme==7?RxOriginalAssets.drawable(this,"egypt",600):null;
    item.setBackground(visual==null?RxSkin.wallpaper(this,j):visual);
    item.setForeground(RxSkin.focus(this));
    LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(122),1);
    size.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(item,size);
    LinearLayout labelBox=col();labelBox.setGravity(Gravity.BOTTOM);
    labelBox.setPadding(dp(8),dp(5),dp(8),dp(8));
    FrameLayout.LayoutParams inside=new FrameLayout.LayoutParams(-1,-1);item.addView(labelBox,inside);
    View mask=new View(this);
    GradientDrawable shade=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
      new int[]{0x00000000,0x20000000,0xe906101d});
    mask.setBackground(shade);item.addView(mask,new FrameLayout.LayoutParams(-1,-1));
    TextView name=text((RxSkin.selected(this)==j?"✓ ":"")+RxSkin.title(this,j),15,true);
    name.setTextColor(0xfff1f1f5);name.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(-1,dp(40),Gravity.BOTTOM);
    overlay.setMargins(dp(5),0,dp(5),dp(5));item.addView(name,overlay);
    item.setOnClickListener(v->{
     RxSkin.apply(this,theme);
     toast(tx("تم تطبيق خلفية ","Applied wallpaper: ")+RxSkin.title(this,theme));
     recreate();
    });
   }
  }
  LinearLayout tips=card(body);
  tips.addView(text(tx("طريقة الاستخدام","How to use"),17,true));
  note(tips,tx("انتقل إلى الرئيسية أو القنوات أو الإعدادات حتى تشوف الستايل الجديد. النصوص والأزرار والوظائف ما تتبدل.",
    "Return to Home, channels or settings to see the new skin. Only visuals are changed."));
 }
}
