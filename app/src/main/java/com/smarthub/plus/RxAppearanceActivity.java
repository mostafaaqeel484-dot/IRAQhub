package com.smarthub.plus;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

/** Wallpapers are rendered locally, without network, video decoder or OEM data. */
public final class RxAppearanceActivity extends HubActivity {
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
  for(int row=0;row<RxSkin.IDS.length;row+=3){
   LinearLayout line=row();
   line.setGravity(Gravity.CENTER_VERTICAL);
   body.addView(line,new LinearLayout.LayoutParams(-1,dp(135)));
   for(int j=row;j<Math.min(row+3,RxSkin.IDS.length);j++){
    final int theme=j;
    FrameLayout item=new FrameLayout(this);
    item.setFocusable(true);item.setClickable(true);item.setDefaultFocusHighlightEnabled(false);
    item.setContentDescription(RxSkin.title(this,j));
    item.setBackground(RxSkin.wallpaper(this,j));
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
