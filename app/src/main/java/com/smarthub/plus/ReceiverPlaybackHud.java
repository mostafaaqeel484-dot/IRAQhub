package com.smarthub.plus;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Non-focusable TV OSD for receiver volume and numeric channel entry.
 * This avoids Toast and does not steal DPAD/OK from the active video.
 */
final class ReceiverPlaybackHud {
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final TextView volumeLabel,numberLabel;
 private final ProgressBar volumeProgress;
 private final LinearLayout volumeBlock;
 private final Runnable hideVolume,hideNumber;

 ReceiverPlaybackHud(Activity context,FrameLayout root){
  float density=context.getResources().getDisplayMetrics().density;
  final int px=(int)(density*10+.5f);
  volumeBlock=new LinearLayout(context);
  volumeBlock.setOrientation(LinearLayout.VERTICAL);
  volumeBlock.setPadding(px*2,px,px*2,px);
  volumeBlock.setBackground(background(0xe9000000,0xffffb84d,density));
  volumeBlock.setFocusable(false);
  volumeLabel=new TextView(context);
  volumeLabel.setTextSize(18);
  volumeLabel.setTextColor(Color.WHITE);
  volumeLabel.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
  volumeBlock.addView(volumeLabel);
  volumeProgress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
  volumeProgress.setMax(100);
  volumeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffffb84d));
  volumeBlock.addView(volumeProgress,new LinearLayout.LayoutParams(-1,(int)(6*density+.5f)));
  FrameLayout.LayoutParams volParams=new FrameLayout.LayoutParams(
    Math.max(px*23,root.getResources().getDisplayMetrics().widthPixels/3),
    -2,Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);
  volParams.bottomMargin=px*5;
  root.addView(volumeBlock,volParams);
  volumeBlock.setVisibility(View.GONE);
  numberLabel=new TextView(context);
  numberLabel.setTextColor(0xffffc75c);
  numberLabel.setTextSize(38);
  numberLabel.setGravity(Gravity.CENTER);
  numberLabel.setPadding(px*3,px,px*3,px);
  numberLabel.setBackground(background(0xee060a11,0xffffc75c,density));
  numberLabel.setFocusable(false);
  FrameLayout.LayoutParams numberParams=new FrameLayout.LayoutParams(
    -2,-2,Gravity.RIGHT|Gravity.TOP);
  numberParams.topMargin=px*7;numberParams.rightMargin=px*4;
  root.addView(numberLabel,numberParams);
  numberLabel.setVisibility(View.GONE);
  hideVolume=()->volumeBlock.setVisibility(View.GONE);
  hideNumber=()->numberLabel.setVisibility(View.GONE);
 }
 private static GradientDrawable background(int fill,int stroke,float density){
  GradientDrawable bg=new GradientDrawable();
  bg.setColor(fill);bg.setStroke(Math.max(1,(int)(density+.5f)),stroke);
  bg.setCornerRadius(density*8);return bg;
 }
 void volume(int percent,boolean muted){
  ui.removeCallbacks(hideVolume);
  volumeLabel.setText(muted?"🔇 كتم الصوت":"🔊  الصوت  "+percent+"%");
  volumeProgress.setProgress(muted?0:Math.max(0,Math.min(100,percent)));
  volumeBlock.setVisibility(View.VISIBLE);
  ui.postDelayed(hideVolume,2000);
 }
 void number(String digits){
  ui.removeCallbacks(hideNumber);
  if(digits==null||digits.isEmpty()){numberLabel.setVisibility(View.GONE);return;}
  numberLabel.setText(digits);
  numberLabel.setVisibility(View.VISIBLE);
  ui.postDelayed(hideNumber,2400);
 }
 void close(){ui.removeCallbacks(hideVolume);ui.removeCallbacks(hideNumber);
  volumeBlock.setVisibility(View.GONE);numberLabel.setVisibility(View.GONE);}
}
