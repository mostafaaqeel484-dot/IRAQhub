package com.smarthub.plus;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NOW/NEXT posters and real programme descriptions on the full signal page. */
final class SignalProgrammeCards implements AutoCloseable {
 private final Activity host;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService workers=Executors.newFixedThreadPool(2);
 private final ImageView[] images=new ImageView[2];
 private final TextView[] titles=new TextView[2],summaries=new TextView[2];
 private String lastKey="";
 private long lastFetch;
 private boolean active,closed;
 SignalProgrammeCards(Activity activity,LinearLayout parent){
  host=activity;
  LinearLayout row=new LinearLayout(activity);
  row.setOrientation(LinearLayout.HORIZONTAL);
  LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);
  rowParams.topMargin=dp(13);
  parent.addView(row,rowParams);
  for(int i=0;i<2;i++){
   LinearLayout card=new LinearLayout(activity);
   card.setOrientation(LinearLayout.HORIZONTAL);
   card.setGravity(Gravity.CENTER_VERTICAL);
   card.setPadding(dp(10),dp(8),dp(10),dp(8));
   GradientDrawable background=new GradientDrawable();
   background.setColor(0xd60d1826);
   background.setCornerRadius(dp(8));
   background.setStroke(dp(1),i==0?0xffc8aa55:0xff61768c);
   card.setBackground(background);
   LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(0,dp(183),1);
   if(i==1)cardParams.leftMargin=dp(8);
   row.addView(card,cardParams);
   ImageView poster=new ImageView(activity);
   poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
   images[i]=poster;
   card.addView(poster,new LinearLayout.LayoutParams(dp(110),dp(157)));
   LinearLayout words=new LinearLayout(activity);
   words.setOrientation(LinearLayout.VERTICAL);
   words.setPadding(dp(10),0,0,0);
   card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
   TextView section=label(i==0?"NOW · الآن":"NEXT · التالي",12,true);
   section.setTextColor(0xffffd16a);
   words.addView(section);
   titles[i]=label("",13,true);
   titles[i].setMaxLines(2);
   words.addView(titles[i]);
   summaries[i]=label("",11,false);
   summaries[i].setMaxLines(4);
   summaries[i].setTextColor(0xffccd5de);
   words.addView(summaries[i]);
  }
 }
 private int dp(int v){return Math.round(v*host.getResources().getDisplayMetrics().density);}
 private TextView label(String value,int size,boolean bold){
  TextView v=new TextView(host);
  v.setText(value);v.setTextColor(0xfff2f6f8);v.setTextSize(size);
  v.setSingleLine(false);
  v.setEllipsize(android.text.TextUtils.TruncateAt.END);
  android.graphics.Typeface font=UserTypeface.get(host);
  if(font!=null)v.setTypeface(font,
    bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
  else if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);
  return v;
 }
 void resume(){active=true;lastKey="";}
 void pause(){active=false;}
 void refresh(SatelliteState observed){
  if(closed||!active||observed==null)return;
  String key=observed.channelId+"|"+observed.title+"|"+observed.startMs+
    "|"+observed.nextTitle+"|"+observed.nextStartMs;
  long now=android.os.SystemClock.elapsedRealtime();
  if(key.equals(lastKey)&&now-lastFetch<45000)return;
  boolean newProgramme=!key.equals(lastKey);
  lastKey=key;lastFetch=now;
  SatelliteState snapshot=observed.copy();
  for(int i=0;i<2;i++){
   final boolean next=i==1;
   String title=next?snapshot.nextTitle:snapshot.title;
   if(newProgramme){
    images[i].setImageDrawable(null);
    titles[i].setText(title.isEmpty()?"—":title);
    summaries[i].setText(title.isEmpty()?"": "...");
   }
   if(title.isEmpty())continue;
   final String expected=key;
   workers.execute(()->load(snapshot,next,expected));
  }
 }
 private void load(SatelliteState snapshot,boolean upcoming,String expected){
  String title=upcoming?snapshot.nextTitle:snapshot.title;
  String description=ProgrammeSynopsisSource.local(host,snapshot,upcoming);
  Bitmap image=null;
  try{
   boolean video=PlaybackResourceMode.active()||
     host.getSharedPreferences("smarthub",0).getBoolean("software_dvb_422_active",false);
   image=video?EpgArtwork.loadInfobarCached(host,snapshot,title,upcoming):
     EpgArtwork.loadInfobar(host,snapshot,title,upcoming);
   if(image==null&&!video){
    OnlineProgramme result=OnlineProgramme.fetch(host,snapshot,title,upcoming);
    if(result!=null){
     image=result.poster;
     if(description.isEmpty()&&result.item!=null)
      description=ProgrammeSynopsisText.clean(result.item.summary,180);
    }
   }
   if(image!=null&&(image.getWidth()>384||image.getHeight()>384)){
    float scale=384f/Math.max(image.getWidth(),image.getHeight());
    image=Bitmap.createScaledBitmap(image,
      Math.max(1,Math.round(image.getWidth()*scale)),
      Math.max(1,Math.round(image.getHeight()*scale)),true);
   }
  }catch(Exception unavailable){/* Programme title and source synopsis remain visible. */}
  final Bitmap result=image;
  final String text=ProgrammeSynopsisText.clean(description,180);
  main.post(()->{
   if(closed||!active||!expected.equals(lastKey))return;
   int index=upcoming?1:0;
   if(result!=null)images[index].setImageBitmap(result);
   summaries[index].setText(text.isEmpty()?"النبذة غير متاحة":text);
  });
 }
 @Override public void close(){closed=true;active=false;workers.shutdownNow();}
}
