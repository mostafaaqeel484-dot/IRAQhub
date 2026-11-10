package com.smarthub.plus;

import android.app.Activity;
import android.content.res.ColorStateList;
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
 * BLUE single press: small live RF/S/Q panel ON TOP of the DVB video surface.
 * Double BLUE retains full signal diagnostics; long BLUE retains companion.
 * No system overlay permission and no fake tuner readings.
 */
final class ReceiverSignalMiniOverlay implements AutoCloseable {
 private final Activity host;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final LinearLayout card;
 private final TextView channel,frequency,strengthLabel,qualityLabel,metrics;
 private final ProgressBar strength,quality;
 private DirectReceiverSignal reader;
 private boolean closed;
 private final Runnable poll=new Runnable(){
  @Override public void run(){
   if(closed||card.getVisibility()!=View.VISIBLE)return;
   try{
    SatelliteState live=SatelliteState.read(host);
    if(reader!=null)reader.refresh(live);
    ReceiverSignalReading frame=DirectReceiverSignal.reading(live);
    android.content.SharedPreferences p=host.getSharedPreferences("smarthub",0);
    boolean owns=frame!=null&&frame.locked==1&&
      live.channelId.equals("DVB:"+p.getString("own_dvb_physical",""))&&
      frame.tp==p.getInt("own_dvb_runtime_tp",-1);
    channel.setText(owns?live.channelName:"TUNER / DVB");
    frequency.setText(owns?p.getString("own_dvb_rf",""):"");
    String sv=frame==null?"":frame.value("strength"),
      qv=frame==null?"":frame.value("quality");
    meter(strength,strengthLabel,"S",sv);
    meter(quality,qualityLabel,"Q",qv);
    metrics.setText("SNR "+(frame==null?"—":value(frame.value("snr")))+
      "    BER "+(frame==null?"—":value(frame.value("ber")))+
      (frame!=null&&frame.locked==1?"    LOCK":"    NO LOCK"));
   }catch(Exception ignored){
    metrics.setText("Signal information unavailable");
   }
   main.postDelayed(this,1100);
  }
 };
 ReceiverSignalMiniOverlay(Activity activity,FrameLayout parent){
  host=activity;
  card=new LinearLayout(activity);
  card.setOrientation(LinearLayout.VERTICAL);
  card.setPadding(dp(13),dp(9),dp(13),dp(10));
  GradientDrawable background=new GradientDrawable();
  background.setColor(0xe90d1622);
  background.setCornerRadius(dp(9));
  background.setStroke(dp(1),0xffffcf69);
  card.setBackground(background);
  channel=label("",15,true);
  frequency=label("",10,false);
  strengthLabel=label("S —",11,false);
  qualityLabel=label("Q —",11,false);
  strength=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
  quality=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
  for(ProgressBar p:new ProgressBar[]{strength,quality}){
   p.setMax(100);
   p.setProgressBackgroundTintList(ColorStateList.valueOf(0xff394652));
  }
  strength.setProgressTintList(ColorStateList.valueOf(0xff66d9ef));
  quality.setProgressTintList(ColorStateList.valueOf(0xff95e28b));
  metrics=label("",10,false);
  metrics.setTextColor(0xffaec1d2);
  card.addView(channel);
  card.addView(frequency);
  card.addView(strengthLabel);
  card.addView(strength,new LinearLayout.LayoutParams(-1,dp(5)));
  card.addView(qualityLabel);
  card.addView(quality,new LinearLayout.LayoutParams(-1,dp(5)));
  card.addView(metrics);
  FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(
    Math.min(dp(430),Math.round(activity.getResources().getDisplayMetrics().widthPixels*.48f)),
    -2,Gravity.RIGHT|Gravity.TOP);
  p.setMargins(0,dp(28),dp(22),0);
  parent.addView(card,p);
  card.setVisibility(View.GONE);
 }
 private int dp(int n){return Math.round(n*host.getResources().getDisplayMetrics().density);}
 private TextView label(String value,int size,boolean bold){
  TextView view=new TextView(host);
  view.setText(value);view.setTextSize(size);
  view.setTextColor(0xffeff5f8);
  view.setSingleLine(true);
  view.setEllipsize(android.text.TextUtils.TruncateAt.END);
  android.graphics.Typeface typeface=UserTypeface.get(host);
  if(typeface!=null)view.setTypeface(typeface,
     bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
  else if(bold)view.setTypeface(null,android.graphics.Typeface.BOLD);
  return view;
 }
 private static String value(String raw){return raw==null||raw.trim().isEmpty()?"—":raw;}
 private static int percent(String raw){
  try{return Math.max(0,Math.min(100,Math.round(Float.parseFloat(raw.replace("%","")))));}
  catch(Exception ignored){return 0;}
 }
 private void meter(ProgressBar bar,TextView label,String name,String reading){
  String text=value(reading);
  label.setText(name+"  "+text);
  bar.setProgress(percent(text));
  bar.setAlpha("—".equals(text)?.25f:1f);
 }
 boolean visible(){return card.getVisibility()==View.VISIBLE;}
 void toggle(){if(visible())hide();else show();}
 void show(){
  if(closed||visible())return;
  card.setVisibility(View.VISIBLE);
  reader=ReceiverInfoRepository.get(host).acquireSignal();
  main.removeCallbacks(poll);
  main.post(poll);
 }
 void hide(){
  card.setVisibility(View.GONE);
  main.removeCallbacks(poll);
  if(reader!=null){
   ReceiverInfoRepository.get(host).releaseSignal(reader);
   reader=null;
  }
 }
 @Override public void close(){closed=true;hide();}
}
