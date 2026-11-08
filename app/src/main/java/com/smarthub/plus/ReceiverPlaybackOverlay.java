package com.smarthub.plus;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Remote-only transient OSD: separate from native DVB video/InfoBar surfaces. */
final class ReceiverPlaybackOverlay {
 private final Activity host;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final LinearLayout volumePanel,numberPanel;
 private final ProgressBar volumeBar;
 private final TextView volumeLabel,numberLabel;
 private final Runnable hideVolume=()->volumePanel.setVisibility(View.GONE);
 ReceiverPlaybackOverlay(Activity activity,FrameLayout root){
  host=activity;
  volumePanel=panel();
  volumePanel.setOrientation(LinearLayout.VERTICAL);
  volumeLabel=label("Volume  —",16);
  volumeBar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
  volumeBar.setMax(100);volumeBar.setProgressTintList(
   android.content.res.ColorStateList.valueOf(0xffffc34e));
  volumePanel.addView(volumeLabel,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout.LayoutParams track=new LinearLayout.LayoutParams(-1,dp(7));
  track.topMargin=dp(7);volumePanel.addView(volumeBar,track);
  FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(dp(290),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
  bottom.bottomMargin=dp(48);
  root.addView(volumePanel,bottom);
  volumePanel.setVisibility(View.GONE);

  numberPanel=panel();
  numberLabel=label("",28);numberLabel.setGravity(Gravity.CENTER);
  numberLabel.setTextColor(0xffffc34e);
  numberPanel.addView(numberLabel,new LinearLayout.LayoutParams(-1,-2));
  FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(dp(170),-2,Gravity.TOP|Gravity.RIGHT);
  top.setMargins(0,dp(28),dp(25),0);
  root.addView(numberPanel,top);
  numberPanel.setVisibility(View.GONE);
 }
 private int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
 private TextView label(String text,int sp){
  TextView t=new TextView(host);t.setText(text);t.setTextColor(0xfff7f7f7);
  t.setTextSize(sp);android.graphics.Typeface font=UserTypeface.get(host);
  if(font!=null)t.setTypeface(font);
  return t;
 }
 private LinearLayout panel(){
  LinearLayout p=new LinearLayout(host);p.setPadding(dp(16),dp(12),dp(16),dp(12));
  GradientDrawable bg=new GradientDrawable();
  bg.setColor(0xe8091019);bg.setCornerRadius(dp(8));
  bg.setStroke(dp(1),0xff81919d);p.setBackground(bg);return p;
 }
 void volume(int percent,boolean muted){
  int safe=Math.max(0,Math.min(100,percent));
  volumeLabel.setText((muted?"🔇 ":"🔊 ")+(muted?"Mute": "Volume  "+safe+"%"));
  volumeBar.setProgress(muted?0:safe);
  volumePanel.setVisibility(View.VISIBLE);
  main.removeCallbacks(hideVolume);
  main.postDelayed(hideVolume,2000);
 }
 void digits(String digits){
  numberLabel.setText(digits);
  numberPanel.setVisibility(digits.isEmpty()?View.GONE:View.VISIBLE);
 }
 void close(){main.removeCallbacks(hideVolume);}
}
