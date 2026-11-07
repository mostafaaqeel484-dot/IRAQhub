package com.smarthub.plus;
import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

public class HubActivity extends Activity {
 protected int BG=0xff0c1324,PANEL=0xff18243c,TILE=0xff253350,ACCENT=0xffb7a6ff,MUTED=0xffacb9d0,WHITE=0xfff5f9ff,AMBER=0xffffd86c;
 private String appliedAppearance;
 protected boolean ar;
 private final MenuKeyHandler menuKey=new MenuKeyHandler(()->{if(!(this instanceof LauncherActivity))ReceiverNavigation.openLauncher(this);});
 @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
  if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_MENU){
   if(event.getAction()==android.view.KeyEvent.ACTION_DOWN)return menuKey.down(true,event.getRepeatCount());
   if(event.getAction()==android.view.KeyEvent.ACTION_UP)return menuKey.up();
  }
  return super.dispatchKeyEvent(event);
 }
 @Override public void onCreate(Bundle state) { super.onCreate(state); ar=prefs().getBoolean("arabic",true);int theme=prefs().getInt("launcher_theme",0);if(theme==1){BG=0xff091c22;PANEL=0xff14343b;TILE=0xff214850;ACCENT=0xff53d8b5;}else if(theme==2){BG=0xff09182c;PANEL=0xff172f4b;TILE=0xff254564;ACCENT=0xff70baff;}BG=0xff09101c;PANEL=0xe8182538;TILE=0xff1c3045;ACCENT=RxSkin.accent(this);MUTED=0xffb1c3d1;WHITE=0xfff1f1f5;AMBER=ACCENT;appliedAppearance=appearance();getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN); }
 private String appearance(){return RxSkin.selected(this)+"/"+prefs().getBoolean("rx_glass_effect",true)+"/"+prefs().getInt("ui_font_percent",100)+"/"+prefs().getBoolean("arabic",true);}
 @Override protected void onResume(){super.onResume();if(!appearance().equals(appliedAppearance))recreate();}
 protected float font(int sp){return sp*Math.max(85,Math.min(125,prefs().getInt("ui_font_percent",100)))/100f;}
 protected SharedPreferences prefs() { return getSharedPreferences("smarthub",MODE_PRIVATE); }
 protected void launchDocument(android.content.Intent request,int code) {
  android.content.pm.ResolveInfo handler=getPackageManager().resolveActivity(request,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
  boolean stub=handler!=null&&handler.activityInfo!=null&&handler.activityInfo.name.contains("Stubs$");
  if((handler==null||stub)&&android.content.Intent.ACTION_OPEN_DOCUMENT.equals(request.getAction())){
   request=new android.content.Intent(request).setAction(android.content.Intent.ACTION_GET_CONTENT);
   request.removeFlags(android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   handler=getPackageManager().resolveActivity(request,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
   stub=handler!=null&&handler.activityInfo!=null&&handler.activityInfo.name.contains("Stubs$");
  }
  if(handler==null||stub){toast(tx("منتقي الملفات غير متاح لهذا النوع. ثبّت منتقي ملفات متوافقاً، أو استخدم رابط الاستيراد إذا كان متاحاً في الصفحة.","No file picker is available for this type. Install a compatible picker, or use URL import where available."));return;}
  try{startActivityForResult(request,code);}catch(android.content.ActivityNotFoundException error){toast(tx("منتقي الملفات غير متاح على الجهاز","No compatible file picker is installed"));}
 }
 protected String tx(String arabic,String english) { return ar?arabic:english; }
 protected int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
 protected LinearLayout col() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
 protected LinearLayout row() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
 protected LinearLayout page(String title) { return page(title,""); }
 protected LinearLayout page(String title,String subtitle) {
  LinearLayout root=col();root.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);root.setBackground(RxSkin.wallpaper(this));root.setPadding(dp(24),dp(18),dp(24),dp(24));
  LinearLayout head=row();TextView name=text(title,27,true);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button back=button(tx("رجوع","Back"));back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(110),dp(48)));root.addView(head);
  if(!subtitle.isEmpty()){TextView sub=text(subtitle,14,false);sub.setTextColor(ACCENT);sub.setPadding(0,dp(5),0,dp(12));root.addView(sub);}
  ScrollView scroll=new ScrollView(this);LinearLayout body=col();body.setPadding(0,dp(12),0,dp(90));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);return body;
 }
 protected TextView text(String value,int sp,boolean bold) { TextView v=new TextView(this);v.setText(value);v.setTextSize(font(sp));v.setTextColor(WHITE);v.setGravity(ar?Gravity.RIGHT:Gravity.LEFT);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);Typeface original=RxOriginalAssets.font(this);
 if(original!=null&&ar)v.setTypeface(original,bold?Typeface.BOLD:Typeface.NORMAL);
 else if(bold)v.setTypeface(null,Typeface.BOLD);
 return v; }
 protected GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d; }
 protected StateListDrawable selection(int color){return RxSkin.focus(this);}
 protected Button button(String label) { Button b=new Button(this);b.setText(label);b.setTextSize(font(16));b.setAllCaps(false);b.setTextColor(WHITE);b.setFocusable(true);b.setMinHeight(dp(46));b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setBackground(RxSkin.focus(this));b.setElevation(dp(2));
 b.setOnFocusChangeListener((view,focused)->{
  view.animate().scaleX(focused?1.012f:1f).setDuration(95).start();
 });
 return b; }
 protected Button action(LinearLayout box,String label,Runnable run) {Button b=button(label);b.setOnClickListener(v->run.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);box.addView(b,p);return b;}
 protected LinearLayout card(LinearLayout box) {LinearLayout c=col();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(RxSkin.glass(this,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);box.addView(c,p);return c;}
 protected EditText field(LinearLayout box,String hint,String value) {EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setTextSize(font(16));e.setHint(hint);e.setText(value);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(RxSkin.glass(this,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);box.addView(e,p);return e;}
 protected CheckBox check(LinearLayout box,String label,boolean yes) {CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(WHITE);c.setTextSize(font(16));c.setChecked(yes);c.setButtonTintList(ColorStateList.valueOf(ACCENT));box.addView(c);return c;}
 protected void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
 protected void note(LinearLayout box,String message) {TextView t=text(message,14,false);t.setTextColor(MUTED);t.setPadding(0,dp(8),0,dp(12));box.addView(t);}
}
