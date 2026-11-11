package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

/** RX-inspired, app-owned visual layer. No tuner, decoder, EPG, or channel logic. */
final class RxSkin {
 private RxSkin(){}
 static final String KEY="rx_wallpaper";
 static final String LAYOUT="rx_home_layout";
 static final String[] IDS={"rx-dark","rx-light","iraq","manchester-united",
      "linux","real-madrid","al-ahly","egypt","mountains","space","sunset","minimal","waterfall","wolf-moon","pyramids"};
 static final String[] EN={"RX Dark","RX Light","Iraq","Manchester United","Linux",
      "Real Madrid","Al Ahly","Egypt","Mountains","Space","Sunset","Minimal Black","Waterfall","Wolf Moon","Pyramids"};
 static final String[] AR={"RX داكن","RX فاتح","العراق","مانشستر يونايتد","لينكس",
      "ريال مدريد","الأهلي","مصر","الجبال","الفضاء","الغروب","أسود بسيط","الشلال","قمر الذئب","الأهرامات"};
 static final int[] ACCENTS={
  0xff7cf0a8,0xff237546,0xff58d893,0xffef4055,0xff89ea51,0xfff2c96b,
  0xffdc4b54,0xffd9ab6f,0xff62bdea,0xffa68cff,0xffff8174,0xffbcc8d8,0xff7ad4ae,0xffa8b9e6,0xffe8c68b
 };
 static int index(String id){
  for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;
  return 0;
 }
 static int selected(Context c){
  SharedPreferences prefs=c.getSharedPreferences("smarthub",0);
  if(prefs.getBoolean("rx_wallpaper_rotate",false)){
   long day=System.currentTimeMillis()/86400000L;
   return (int)Math.floorMod(day,(long)IDS.length);
  }
  return index(prefs.getString(KEY,"rx-dark"));
 }
 static int accent(Context c){
  int theme=c.getSharedPreferences("smarthub",0).getInt("launcher_theme",0);
  return AppearancePalette.accent(theme,ACCENTS[selected(c)]);
 }
 static String title(Context c,int i){
  return c.getSharedPreferences("smarthub",0).getBoolean("arabic",true)?AR[i]:EN[i];
 }
 static void apply(Context c,int i){
  if(i<0||i>=IDS.length)return;
  c.getSharedPreferences("smarthub",0).edit()
   .putString(KEY,IDS[i]).putBoolean("rx_wallpaper_rotate",false)
   .putInt("launcher_theme",0)
   .putString("osd_accent",String.format(java.util.Locale.ROOT,"#%06X",ACCENTS[i]&0xffffff))
   .putLong("osd_changed_at",System.currentTimeMillis()).apply();
 }
 static GradientDrawable glass(Context c,boolean emphasized){
  int accent=accent(c),radius=dp(c,emphasized?20:16);
  if(PlaybackResourceMode.lite(c)){
   GradientDrawable light=new GradientDrawable();
   light.setColor(0xec0b121c);light.setCornerRadius(radius);
   light.setStroke(dp(c,1),emphasized?accent:0x774e6a79);
   return light; // No multi-stop gradients during live software 4:2:2.
  }
  boolean frosted=c.getSharedPreferences("smarthub",0).getBoolean("rx_glass_effect",true);
  GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   frosted?new int[]{0xe6293246,0xd3131b30,0xe508101d}:
    new int[]{0xff1a2433,0xff151d29,0xff111a25});
  bg.setCornerRadius(radius);
  bg.setStroke(dp(c,emphasized?2:1),emphasized?accent:0x776e839d);
  return bg;
 }
 static GradientDrawable focused(Context c){
  if(PlaybackResourceMode.lite(c)){
   GradientDrawable flat=new GradientDrawable();flat.setColor(0xf01d2d36);
   flat.setCornerRadius(dp(c,17));flat.setStroke(dp(c,2),accent(c));return flat;
  }
  GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{0xf0435876,0xf025374f,0xf0182538});
  bg.setCornerRadius(dp(c,17));
  bg.setStroke(dp(c,2),accent(c));
  return bg;
 }
 static StateListDrawable focus(Context c){
  StateListDrawable state=new StateListDrawable();
  state.addState(new int[]{android.R.attr.state_focused},focused(c));
  state.addState(new int[]{android.R.attr.state_selected},focused(c));
  state.addState(new int[]{android.R.attr.state_pressed},focused(c));
  state.addState(new int[]{},glass(c,false));return state;
 }
 static GradientDrawable bar(Context c,int opacity){
  if(PlaybackResourceMode.lite(c)){
   GradientDrawable flat=new GradientDrawable();
   flat.setColor((Math.max(96,Math.min(248,opacity))<<24)|0x000b1118);
   flat.setCornerRadius(dp(c,19));flat.setStroke(dp(c,1),accent(c));return flat;
  }
  // Respect the existing InfoBar opacity control (30..95%): visuals only.
  int level=Math.max(64,Math.min(248,opacity));
  GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
    new int[]{((Math.max(32,level-25))<<24)|0x000b1220,
              (level<<24)|0x000c1525,
              ((Math.min(255,level+8))<<24)|0x00091020});
  bg.setCornerRadius(dp(c,19));
  bg.setStroke(dp(c,1),accent(c)&0x00ffffff | 0x99000000);
  return bg;
 }
 static Drawable wallpaper(Context c){return new Wallpaper(c,selected(c));}
 static Drawable wallpaper(Context c,int i){return new Wallpaper(c,i);}
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static final class Wallpaper extends Drawable {
  final int style;
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  Wallpaper(Context c,int i){style=i;}
  @Override public void draw(Canvas original){
   Rect r=getBounds();if(r.width()<=0||r.height()<=0)return;
   int saved=original.save();original.translate(r.left,r.top);
   original.scale(r.width()/1600f,r.height()/900f);
   Canvas c=original;
   int start,end;
   switch(style){
    case 1:start=0xffe3e6ee;end=0xff586577;break;
    case 2:start=0xff061926;end=0xff122b34;break;
    case 3:start=0xff17090f;end=0xff5c101c;break;
    case 4:start=0xff07170e;end=0xff173d29;break;
    case 5:start=0xff100e1a;end=0xff352c45;break;
    case 6:start=0xff180d14;end=0xff371923;break;
    case 7:start=0xff191b24;end=0xff4d4336;break;
    case 8:start=0xff071d34;end=0xff17607d;break;
    case 9:start=0xff02070d;end=0xff14102a;break;
    case 10:start=0xff230c2a;end=0xffaa3e4a;break;
    case 11:start=0xff05070a;end=0xff111723;break;
    default:start=0xff090b14;end=0xff1d2340;break;
   }
   p.setShader(new LinearGradient(0,0,1600,900,start,end,Shader.TileMode.CLAMP));
   c.drawRect(0,0,1600,900,p);p.setShader(null);
   for(int i=0;i<6;i++){
    p.setColor((0x1c+(i%2)*10)<<24 | (RxSkin.ACCENTS[style]&0x00ffffff));
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f+i*1.2f);
    c.drawOval(-500+i*125,-310+i*46,1400+i*190,920+i*120,p);
   }
   p.setStyle(Paint.Style.FILL);
   if(style==2)iraq(c);
   else if(style==3||style==5||style==6)stadium(c);
   else if(style==4)linux(c);
   else if(style==7)egypt(c);
   else if(style==8||style==10)mountains(c);
   else if(style==9)space(c);
   else if(style==0)rings(c);
   // Keep the left/centre TV menu readable, independent of artwork luminance.
   p.setShader(new LinearGradient(0,0,0,900,
    new int[]{0x69040a14,0x52050b17,0xdc02060c},null,Shader.TileMode.CLAMP));
   c.drawRect(0,0,1600,900,p);p.setShader(null);
   original.restoreToCount(saved);
  }
  private void iraq(Canvas c){
   p.setColor(0x26ffffff);c.drawRect(0,85,1600,225,p);
   p.setColor(0x47d4283d);c.drawRect(0,22,1600,115,p);
   p.setColor(0x4443af72);c.drawRect(0,225,1600,295,p);
   p.setColor(0x99435769);c.drawRect(880,490,1600,670,p);
   p.setColor(0xaa070f19);
   for(int x=820;x<1650;x+=72)c.drawRect(x,380-(x%5)*20,x+60,720,p);
   p.setColor(0xccb9b4a3);c.drawOval(1020,350,1220,540,p);
   p.setColor(0xff18343e);c.drawRect(1010,470,1230,690,p);
   p.setColor(0xff569b71);c.drawLine(0,700,1600,750,p);
   p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
   p.setTextSize(102);p.setColor(0x92ffffff);c.drawText("IRAQ",1060,250,p);
  }
  private void stadium(Canvas c){
   p.setColor(0x9c090e19);c.drawOval(490,250,1810,1100,p);
   p.setColor(0xff171e29);c.drawOval(550,300,1710,950,p);
   p.setColor(style==3?0xffa32337:style==5?0xffc9a746:0xffa62b38);
   for(int i=0;i<13;i++){p.setStrokeWidth(7);c.drawLine(560+i*92,470,700+i*72,700,p);}
   p.setColor(0xff153d2a);c.drawOval(740,600,1430,890,p);
   p.setColor(0x44ffffff);p.setStrokeWidth(5);
   c.drawLine(1000,610,1000,845,p);
   p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
   p.setTextSize(88);p.setColor(0xd7ffffff);
   c.drawText(style==3?"UNITED":style==5?"MADRID":"AL AHLY",790,285,p);
  }
  private void linux(Canvas c){
   p.setTypeface(Typeface.create("monospace",Typeface.BOLD));p.setTextSize(45);
   p.setColor(0x6689ef90);
   for(int i=0;i<18;i++)c.drawText(i%3==0?"$ sudo apt update":i%3==1?
    "user@iraqhub:~$":"Linux kernel ready",650,80+i*49,p);
   p.setColor(0xa5e3eeeb);c.drawOval(1130,430,1430,745,p);
   p.setColor(0xff07120e);c.drawOval(1195,455,1380,680,p);
   p.setColor(0xffffc55e);c.drawOval(1250,520,1320,555,p);
  }
  private void egypt(Canvas c){p.setColor(0xffb69865);Path pyramid=new Path();
   pyramid.moveTo(800,760);pyramid.lineTo(1120,280);pyramid.lineTo(1470,760);pyramid.close();c.drawPath(pyramid,p);
   p.setColor(0x88eac184);pyramid.reset();pyramid.moveTo(1190,760);pyramid.lineTo(1400,440);pyramid.lineTo(1600,760);pyramid.close();c.drawPath(pyramid,p);}
  private void mountains(Canvas c){for(int i=0;i<5;i++){
   p.setColor(i%2==0?0xff122d48:0xff30566d);
   Path m=new Path();m.moveTo(0,900);m.lineTo(0,670+i*35);
   for(int x=0;x<=1800;x+=160)m.lineTo(x,270+i*125+((x/160+i)%4)*58);
   m.lineTo(1800,900);m.close();c.drawPath(m,p);}}
  private void space(Canvas c){
   p.setColor(0xffffd0a7);c.drawCircle(1220,320,185,p);
   p.setColor(0xff2f243c);c.drawCircle(1280,275,156,p);
   p.setColor(0x88ebf2ff);
   for(int i=0;i<82;i++){int x=(i*157+91)%1600,y=(i*131+33)%800;c.drawCircle(x,y,i%9==0?2.8f:1.1f,p);}
  }
  private void rings(Canvas c){
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(42);p.setColor(0x6689a8ff);
   c.drawOval(960,150,1640,620,p);p.setStrokeWidth(10);p.setColor(0xffa19aae);
   c.drawOval(1080,220,1510,540,p);p.setStyle(Paint.Style.FILL);
  }
  @Override public void setAlpha(int a){}
  @Override public void setColorFilter(android.graphics.ColorFilter f){}
  @Override public int getOpacity(){return PixelFormat.OPAQUE;}
 }
}
