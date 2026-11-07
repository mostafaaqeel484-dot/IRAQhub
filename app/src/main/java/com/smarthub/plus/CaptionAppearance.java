package com.smarthub.plus;

import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.media3.ui.SubtitleView;

/** One remote-controlled text size for decoded captions, OCR and subtitle files. */
final class CaptionAppearance {
 private static final java.util.Map<View,Integer> applied=new java.util.WeakHashMap<>();
 static int size(Context c){return Math.max(12,Math.min(64,c.getSharedPreferences("smarthub",0).getInt("overlay_caption_font",28)));}
 static void set(Context c,int value){c.getSharedPreferences("smarthub",0).edit().putInt("overlay_caption_font",Math.max(12,Math.min(64,value))).putBoolean("caption_font_override",true).apply();}
 static void apply(TextView v){int size=size(v.getContext());if(java.util.Objects.equals(applied.get(v),size))return;applied.put(v,size);v.setTextSize(size);v.setTextColor(v.getContext().getSharedPreferences("smarthub",0).getInt("overlay_caption_color",Color.WHITE));v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setShadowLayer(3,1,2,Color.BLACK);}
 @androidx.media3.common.util.UnstableApi static void apply(SubtitleView v){if(v==null)return;Context c=v.getContext();if(c.getSharedPreferences("smarthub",0).getBoolean("caption_font_override",false)){int size=size(c);if(java.util.Objects.equals(applied.get(v),size))return;applied.put(v,size);v.setApplyEmbeddedFontSizes(false);v.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,size);}}
 static void controls(LinearLayout parent,boolean arabic,Runnable changed){
  Context c=parent.getContext();TextView preview=new TextView(c);preview.setTextColor(Color.WHITE);preview.setBackgroundColor(0xb0000000);preview.setGravity(Gravity.CENTER);preview.setMaxLines(2);
  Runnable refresh=()->{preview.setText((arabic?"حجم خط الترجمة: ":"Subtitle font size: ")+size(c)+" sp\n"+(arabic?"معاينة الترجمة العربية":"Subtitle preview · معاينة الترجمة"));apply(preview);if(changed!=null)changed.run();};
  LinearLayout row=new LinearLayout(c);row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  String[] labels={"A−",arabic?"إعادة 28":"Reset 28","A+"};int[] delta={-2,0,2};
  for(int i=0;i<labels.length;i++){final int d=delta[i];Button b=new Button(c);b.setText(labels[i]);b.setAllCaps(false);b.setFocusable(true);b.setContentDescription(d<0?(arabic?"تصغير خط الترجمة":"Decrease subtitle font"):d>0?(arabic?"تكبير خط الترجمة":"Enlarge subtitle font"):labels[i]);b.setOnClickListener(v->{set(c,d==0?28:size(c)+d);refresh.run();});row.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
  parent.addView(row);parent.addView(preview,new LinearLayout.LayoutParams(-1,-2));refresh.run();
 }
 /** Keep translated text above the captured region, even when the font grows. */
 static void positionAboveOcr(Context c,WindowManager.LayoutParams p){int screen=c.getResources().getDisplayMetrics().heightPixels,top=Math.max(35,Math.min(90,c.getSharedPreferences("smarthub",0).getInt("native_ocr_top",68)));p.gravity=Gravity.BOTTOM;p.y=screen-screen*top/100+Math.round(8*c.getResources().getDisplayMetrics().density);}
}
