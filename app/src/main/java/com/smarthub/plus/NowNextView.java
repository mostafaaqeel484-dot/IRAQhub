package com.smarthub.plus;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import java.text.SimpleDateFormat;
import java.util.*;

/** Lightweight native-TV OSD with channel left and service-provider branding at the far right. */
final class NowNextView extends FrameLayout {
 private final ImageView currentPoster,nextPoster,logo,providerLogo;
 private final TextView currentTitle,nextTitle,heading,nowLine,nextLine,status,clock;
 private final TextView broadcast,remaining,technical,weather,strengthLabel,qualityLabel,signalDetail,signalSource,encryptionLine,bitrateLabel,providerName,providerCaption;
 private final LinearLayout providerPanel,resolutionStrip,details,bar,leftPosterBox,rightPosterBox;
 private final TextView[] resolutionChips=new TextView[5];
 private final TextView[] featureChips=new TextView[6];
 private final ProgressBar progress,strengthBar,qualityBar;
 private final android.content.SharedPreferences prefs;
 private final int ink=0xfff1f1f5,muted=0xffafbdc9;
 private boolean compact,expanded;private float textScale=1;private int accent=0xffdc49ec;private MatchTickerView ticker;
 NowNextView(Context context){
  super(context);
  prefs=context.getSharedPreferences("smarthub",0);
  accent=RxSkin.accent(context);
  try{accent=Color.parseColor(prefs.getString("osd_accent",
      String.format(Locale.ROOT,"#%06X",accent&0xffffff)));}catch(Exception ignored){}
  setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  // RX replaces the former stack of floating info panels with a single clean
  // low-profile bar. Posters sit INSIDE the bar and never mask the TV picture.
  final boolean detailed=prefs.getBoolean("osd_rx_detailed",false);
  int barH=dp(detailed?238:183);
  int tickerH=prefs.getBoolean("sports_ticker",false)?dp(62):0;
  compact=barH<dp(145);
  textScale=Math.max(.90f,Math.min(1.06f,barH/(150f*
       getResources().getDisplayMetrics().density)));
  int alpha=Math.max(30,Math.min(95,prefs.getInt("osd_opacity",88)))*255/100;
  bar=column();bar.setGravity(Gravity.CENTER_VERTICAL);
  bar.setPadding(dp(20),dp(8),dp(20),dp(7));
  GradientDrawable dark=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
   new int[]{Color.argb(alpha,0,0,0),Color.argb(Math.min(255,alpha+12),6,6,8)});
  dark.setCornerRadius(dp(9));bar.setBackground(dark);
  bar.setElevation(dp(6));
  LayoutParams barPosition=new LayoutParams(-1,barH,Gravity.BOTTOM);
  barPosition.setMargins(dp(8),0,dp(8),dp(8));
  addView(bar,barPosition);

  LinearLayout primary=new LinearLayout(context);
  primary.setGravity(Gravity.CENTER_VERTICAL);
  bar.addView(primary,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout brand=column();brand.setGravity(Gravity.CENTER);
  logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  brand.addView(logo,new LinearLayout.LayoutParams(dp(79),dp(55)));
  providerPanel=column();providerPanel.setGravity(Gravity.CENTER);
  providerCaption=label(tx("المزود","PROVIDER"),9,false);
  providerCaption.setTextColor(muted);
  providerLogo=new ImageView(context);
  providerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  providerLogo.setVisibility(GONE);
  providerName=label("",10,false);providerName.setGravity(Gravity.CENTER);
  providerName.setTextColor(muted);
  providerPanel.addView(providerCaption);
  providerPanel.addView(providerLogo,new LinearLayout.LayoutParams(dp(44),dp(25)));
  providerPanel.addView(providerName);
  providerPanel.setVisibility(detailed?VISIBLE:GONE);
  brand.addView(providerPanel);
  primary.addView(brand,new LinearLayout.LayoutParams(dp(detailed?104:89),-2));

  LinearLayout programme=column();
  programme.setPadding(dp(14),dp(2),dp(12),0);
  primary.addView(programme,new LinearLayout.LayoutParams(0,-2,1.5f));
  heading=label("IRAQhub",compact?18:22,true);
  heading.setTextColor(0xff3fe44d);programme.addView(heading);
  broadcast=label("",10,false);broadcast.setTextColor(0xffe0e0e0);
  programme.addView(broadcast);
  nowLine=label("",compact?13:16,true);
  programme.addView(nowLine);
  nextLine=label("",compact?11:13,false);
  nextLine.setTextColor(0xffbac6d4);programme.addView(nextLine);
  progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
  progress.setMax(1000);
  progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xfff14640));
  progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff344657));
  LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,dp(4));
  progressParams.topMargin=dp(6);programme.addView(progress,progressParams);
  remaining=label("",compact?9:11,false);
  remaining.setTextColor(accent);
  programme.addView(remaining);
  status=label("",10,false);status.setTextColor(muted);
  status.setVisibility(GONE);programme.addView(status);

  LinearLayout posterTray=new LinearLayout(context);
  posterTray.setGravity(Gravity.CENTER);
  posterTray.setPadding(dp(9),0,dp(9),0);
  primary.addView(posterTray,new LinearLayout.LayoutParams(dp(172),-2));
  posterTray.setVisibility(GONE);
  LinearLayout left=column();left.setGravity(Gravity.CENTER);
  currentTitle=label(tx("الآن","NOW"),9,true);
  currentTitle.setGravity(Gravity.CENTER);currentTitle.setTextColor(accent);
  currentPoster=poster();
  left.addView(currentTitle);
  left.addView(currentPoster,new LinearLayout.LayoutParams(dp(66),dp(85)));
  posterTray.addView(left,new LinearLayout.LayoutParams(0,-2,1));
  LinearLayout right=column();right.setGravity(Gravity.CENTER);
  nextTitle=label(tx("التالي","NEXT"),9,true);
  nextTitle.setGravity(Gravity.CENTER);nextTitle.setTextColor(0xffd3d7db);
  nextPoster=poster();
  right.addView(nextTitle);
  right.addView(nextPoster,new LinearLayout.LayoutParams(dp(66),dp(85)));
  posterTray.addView(right,new LinearLayout.LayoutParams(0,-2,1));
  // Expanded artwork is on opposite sides of the programme, never a paired central strip.
  leftPosterBox=left;rightPosterBox=right;
  posterTray.removeAllViews();primary.removeView(posterTray);
  boolean swap=prefs.getBoolean("osd_swap_posters",false);
  primary.addView(swap?right:left,1,new LinearLayout.LayoutParams(dp(98),-2));
  primary.addView(swap?left:right,3,new LinearLayout.LayoutParams(dp(98),-2));
  left.setVisibility(GONE);right.setVisibility(GONE);

  LinearLayout signal=column();signal.setGravity(Gravity.CENTER_VERTICAL);
  primary.addView(signal,new LinearLayout.LayoutParams(dp(compact?120:152),-2));
  clock=label("",compact?15:18,true);
  clock.setTextDirection(View.TEXT_DIRECTION_LTR);
  clock.setGravity(Gravity.RIGHT);signal.addView(clock);
  strengthLabel=label("S: —",10,false);
  strengthBar=meter(0xff28cbfa);
  qualityLabel=label("Q: —",10,false);
  qualityBar=meter(0xffcb27e6);
  signal.addView(strengthLabel);
  signal.addView(strengthBar,new LinearLayout.LayoutParams(-1,dp(4)));
  signal.addView(qualityLabel);
  signal.addView(qualityBar,new LinearLayout.LayoutParams(-1,dp(4)));

  // Bind targets for optional RX Details mode. All data sources and getters
  // remain intact; only their visibility changes.
  
  resolutionStrip=new LinearLayout(context);
  resolutionStrip.setGravity(Gravity.CENTER_VERTICAL);
  for(int i=0;i<resolutionChips.length;i++){
   TextView chip=label(ReceiverTechnicalBadges.RESOLUTIONS[i],10,true);
   chip.setGravity(Gravity.CENTER);chip.setPadding(dp(3),0,dp(3),0);
   resolutionChips[i]=chip;
   resolutionStrip.addView(chip,new LinearLayout.LayoutParams(dp(44),dp(23)));
  }
  weather=label("",10,false);weather.setTextColor(muted);
  signalSource=label("",10,false);signalSource.setTextColor(muted);
  signalDetail=label("",10,false);signalDetail.setTextColor(muted);
  bitrateLabel=label("",10,false);bitrateLabel.setTextColor(muted);
  encryptionLine=label("",10,true);encryptionLine.setTextColor(accent);
  technical=label("",10,false);technical.setTextColor(muted);
  details=new LinearLayout(context);
  details.setGravity(Gravity.CENTER_VERTICAL);
  programme.addView(resolutionStrip);
  LinearLayout featureRow=new LinearLayout(context);featureRow.setGravity(Gravity.CENTER_VERTICAL);
  String[] tags={"TXT","H265","SUB","EPG","CA","PIP"};
  for(int i=0;i<tags.length;i++){
   TextView c=label(tags[i],9,true);c.setGravity(Gravity.CENTER);c.setPadding(dp(3),dp(1),dp(3),dp(1));
   featureChips[i]=c;featureRow.addView(c,new LinearLayout.LayoutParams(dp(43),dp(21)));
  }
  programme.addView(featureRow);
  details.addView(encryptionLine);
  details.addView(signalSource);
  details.addView(signalDetail);
  details.addView(bitrateLabel);
  details.addView(weather);
  details.addView(technical,new LinearLayout.LayoutParams(0,-2,1));
  details.setVisibility(VISIBLE);
  bar.addView(details,new LinearLayout.LayoutParams(-1,-2));

  if(tickerH>0){
   ticker=new MatchTickerView(context);
   LayoutParams tick=new LayoutParams(-1,tickerH,Gravity.BOTTOM);
   tick.setMargins(dp(10),0,dp(10),barH+dp(8));
   addView(ticker,tick);
  }
  setExpanded(detailed);
  setAlpha(0f);animate().alpha(1f).setDuration(150).start();
 }

 void setExpanded(boolean value){
  expanded=value;
  leftPosterBox.setVisibility(value&&prefs.getBoolean("osd_posters",true)?VISIBLE:GONE);
  rightPosterBox.setVisibility(value&&prefs.getBoolean("osd_posters",true)?VISIBLE:GONE);
  providerPanel.setVisibility(value?VISIBLE:GONE);
  FrameLayout.LayoutParams position=(FrameLayout.LayoutParams)bar.getLayoutParams();
  if(position!=null){position.height=dp(value?238:183);bar.setLayoutParams(position);}
 }
 void bind(SatelliteState s,Bitmap current,Bitmap next,Bitmap channelLogo,String message){
  ReceiverInfoRepository.Info info=ReceiverInfoRepository.get(getContext()).info(s);
  String badge=info.badge;
  // Inactive standards are visible but muted. Only the verified resolution is gold.
  for(int i=0;i<resolutionChips.length;i++){
   String kind=ReceiverTechnicalBadges.RESOLUTIONS[i];
   boolean active=kind.equals(badge);
   TextView chip=resolutionChips[i];
   chip.setTextColor(active?0xff081322:0xff9caebd);
   GradientDrawable background=new GradientDrawable();
   background.setColor(active?accent:0xa0263749);
   background.setStroke(dp(1),active?0xfff1f1f5:0x66899caf);
   background.setCornerRadius(dp(5));
   chip.setBackground(background);
   chip.setContentDescription(active?kind+" · "+tx("الدقة الفعلية","observed resolution"):kind+" · "+tx("غير نشط","inactive"));
  }
  resolutionStrip.setContentDescription(badge.isEmpty()?tx("الدقة غير متاحة","Video resolution not available"):badge);
  broadcast.setText(info.broadcast);broadcast.setVisibility(info.broadcast.isEmpty()?GONE:VISIBLE);
  currentPoster.setImageBitmap(current);nextPoster.setImageBitmap(next);
  if(current==null)currentPoster.setImageDrawable(null);if(next==null)nextPoster.setImageDrawable(null);currentPoster.setAlpha(current==null?.2f:1);nextPoster.setAlpha(next==null?.2f:1);
  currentPoster.setContentDescription(tx("الآن · ","NOW · ")+s.title);nextPoster.setContentDescription(tx("التالي · ","NEXT · ")+s.nextTitle);currentTitle.setText(tx("الآن · ","NOW · ")+shortTime(s.startMs));nextTitle.setText(tx("التالي · ","NEXT · ")+shortTime(s.nextStartMs));
  String number=s.channelId.replaceFirst("^Aurum:","");
  if(s.channelId.startsWith("DVB:")){
   int ordinal=prefs.getInt("own_dvb_sat_number",0);
   number=ordinal>0&&s.channelId.equals(prefs.getString("own_dvb_sat_number_channel",""))?
     Integer.toString(ordinal):"";
  }
  if(!number.matches("[0-9]+"))number="";
  heading.setText((number.isEmpty()?"":number+"  ")+(s.channelName.isEmpty()?"IRAQhub":s.channelName));
  String provider=info.provider;providerName.setText(provider.isEmpty()?"":provider.replace(" (CHL)",""));providerCaption.setText(tx("المزود","PROVIDER")+(provider.endsWith(" (CHL)")?" · CHL":""));providerPanel.setContentDescription(tx("مزود الخدمة: ","Service provider: ")+provider);
  nowLine.setText(tx("الآن  ","NOW  ")+(s.title.isEmpty()?tx("بيانات البرنامج غير متاحة","Programme unavailable"):s.title)+range(s.startMs,s.endMs));
  nextLine.setText(tx("التالي  ","NEXT  ")+(s.nextTitle.isEmpty()?tx("غير متاح من EPG","Unavailable from EPG"):s.nextTitle)+range(s.nextStartMs,s.nextEndMs));
  if(channelLogo==null)logo.setImageResource(R.drawable.channel_placeholder);else logo.setImageBitmap(channelLogo);logo.setContentDescription("Channel logo · "+s.channelName);
  long length=s.endMs-s.startMs;progress.setProgress(length>0?(int)Math.max(0,Math.min(1000,(System.currentTimeMillis()-s.startMs)*1000/length)):0);progress.setVisibility(length>0?VISIBLE:INVISIBLE);
  ProgrammeClock timer=new ProgrammeClock(s.startMs,s.endMs,System.currentTimeMillis());remaining.setText(timer.known?tx("متبقي  ","REMAINING  ")+ProgrammeClock.format(timer.remainingMs,false):"");remaining.setVisibility(timer.known?VISIBLE:GONE);
  SimpleDateFormat day=new SimpleDateFormat("EEE dd MMM yyyy",prefs.getBoolean("arabic",true)?new Locale("ar"):Locale.ENGLISH);day.setTimeZone(TimeZone.getTimeZone("Asia/Baghdad"));clock.setText(shortTime(System.currentTimeMillis())+"\n"+day.format(new Date()));clock.setVisibility(prefs.getBoolean("osd_clock",true)?VISIBLE:GONE);
  weather.setText(WeatherRepository.label(getContext()));weather.setVisibility(prefs.getBoolean("osd_weather",false)?VISIBLE:GONE);
  String strength=ReceptionData.value(getContext(),s,"strength"),quality=ReceptionData.value(getContext(),s,"quality");strengthLabel.setText("S: "+(strength.isEmpty()?"—":strength));qualityLabel.setText("Q: "+(quality.isEmpty()?"—":quality));strengthBar.setProgress(percent(strength),true);qualityBar.setProgress(percent(quality),true);strengthBar.setVisibility(strength.isEmpty()?INVISIBLE:VISIBLE);qualityBar.setVisibility(quality.isEmpty()?INVISIBLE:VISIBLE);
  signalDetail.setText(info.signalDetail());signalSource.setText(s.source.startsWith("IPTV")?"IPTV":DirectReceiverSignal.sourceLabel(s,prefs.getBoolean("arabic",true)));signalSource.setVisibility(compact?GONE:VISIBLE);
  String ca=info.encryption;
  boolean encrypted=!ca.isEmpty()&&!ca.startsWith("FTA")&&!ca.contains("مفتوحة");
  encryptionLine.setText(ca.isEmpty()?tx("CA —","CA —"):encrypted?"🔒 "+ca:"🔓 "+ca);
  GradientDrawable caBackground=new GradientDrawable();
  caBackground.setColor(encrypted?0x704c3545:0x66314f42);
  caBackground.setCornerRadius(dp(5));
  caBackground.setStroke(dp(1),encrypted?0xffe3aa7f:0xff74bea1);
  encryptionLine.setBackground(caBackground);
  encryptionLine.setPadding(dp(6),dp(3),dp(6),dp(3));
  encryptionLine.setGravity(Gravity.CENTER);
  encryptionLine.setContentDescription(ca.isEmpty()?tx("معلومات التشفير غير متاحة","CA information unknown"):ca);
  ArrayList<String> details=new ArrayList<>();
  bitrateLabel.setText("Bitrate  "+(info.bitrate.isEmpty()?"—":info.bitrate));
  bitrateLabel.setContentDescription(tx("معدل البت: ","Bitrate: ")+
     (info.bitrate.isEmpty()?tx("غير متاح","unavailable"):info.bitrate));
  if(!info.broadcast.isEmpty())details.add(info.broadcast);
  if(s.source.startsWith("IPTV")){String audio=prefs.getString("osd_audio","");if(!audio.isEmpty())details.add(audio);}
  if(s.channelId.equals(prefs.getString("subtitle_channel_id",""))&&s.startMs==prefs.getLong("subtitle_event_start_ms",0)&&prefs.getBoolean("overlay_caption",false))details.add("SUB · "+prefs.getString("subtitle_provider","")+String.format(Locale.ROOT," %+.3f s",prefs.getLong("subtitle_offset",0)/1000.0));
  SportsMatch related=MatchRepository.get(getContext()).forProgramme(s);if(related!=null)details.add(related.league+" · "+related.home+" "+related.score()+" "+related.away+" · "+related.state());
  technical.setText(String.join("   ·   ",details));technical.setVisibility(details.isEmpty()?GONE:VISIBLE);
  String[] featureNames={"TXT","H265","SUB","EPG","CA","PIP"};
  boolean[] featureActive={
   prefs.getBoolean("native_subtitle_teletext_active",false),
   "H265".equalsIgnoreCase(prefs.getString("decoder_video_codec","")),
   prefs.getBoolean("live_translate",false)||prefs.getBoolean("native_caption_ocr",false),
   !s.title.isEmpty()&&s.startMs>0,
   encrypted,
   prefs.getBoolean("dvb_pip_active",false)
  };
  for(int i=0;i<featureChips.length;i++){
   GradientDrawable chip=new GradientDrawable();
   chip.setColor(featureActive[i]?0xffeeeeef:0xff363638);
   chip.setCornerRadius(dp(4));
   featureChips[i].setBackground(chip);
   featureChips[i].setTextColor(featureActive[i]?0xff111114:0xff85858b);
   featureChips[i].setContentDescription(featureNames[i]+(featureActive[i]?" active":" inactive"));
  }
  if(ticker!=null){ArrayList<SportsMatch> selected=new ArrayList<>();for(SportsMatch m:MatchRepository.get(getContext()).snapshot()){LeagueRepository.League l=LeagueRepository.byId(m.leagueId);if(l!=null&&MatchRepository.get(getContext()).selected(l)&&m.kickoff>System.currentTimeMillis()-86400000L&&m.kickoff<System.currentTimeMillis()+2*86400000L)selected.add(m);}ticker.bind(selected);}
  boolean show=prefs.getBoolean("osd_posters",true);currentTitle.setVisibility(s.title.isEmpty()?INVISIBLE:VISIBLE);nextTitle.setVisibility(s.nextTitle.isEmpty()?INVISIBLE:VISIBLE);currentPoster.setVisibility(s.title.isEmpty()||current==null||!show?INVISIBLE:VISIBLE);nextPoster.setVisibility(s.nextTitle.isEmpty()||next==null||!show?INVISIBLE:VISIBLE);
  ReceiverSignalReading sample=DirectReceiverSignal.reading(s);
  boolean noSignal=s.channelId.startsWith("DVB:")&&sample!=null&&sample.locked==0&&
    !prefs.getBoolean("dvb_tuning",false)&&
    s.channelId.equals("DVB:"+prefs.getString("own_dvb_physical",""));
  setStatus(noSignal?tx("لا يوجد إشارة","No signal"):message);
 }
 void metadata(MediaRepository.Item item){if(item!=null&&!item.id.startsWith("epg:")){String detail=item.release+(item.ratings.isEmpty()?"":" · ★ "+item.ratings);if(!detail.isEmpty()){remaining.setText(remaining.getText()+" · "+detail);remaining.setVisibility(VISIBLE);}if(prefs.getBoolean("osd_description",true)&&!item.summary.isEmpty())setStatus(item.summary);}}
 void provider(Bitmap image){providerLogo.setImageBitmap(image);providerLogo.setVisibility(image==null?GONE:VISIBLE);providerName.setVisibility(image==null?VISIBLE:GONE);}
 void setStatus(String value){
  String text=value==null?"":value.replace('\n',' ');
  status.setText(text);
  status.setTextColor(0xffff7878);
  status.setVisibility(!text.isEmpty()?VISIBLE:GONE);
}
 private String tx(String ar,String en){return prefs.getBoolean("arabic",true)?ar:en;}
 private String range(long a,long b){return a>0?"  ·  "+shortTime(a)+(b>a?" – "+shortTime(b):""):"";}
 private static String shortTime(long at){if(at<=0)return "";SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("Asia/Baghdad"));return f.format(new Date(at));}
 private void divider(LinearLayout parent){View v=new View(getContext());v.setBackgroundColor(0x337da4ba);parent.addView(v,new LinearLayout.LayoutParams(dp(1),dp(84)));}
 private GradientDrawable card(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private ProgressBar meter(int color){ProgressBar p=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setProgressTintList(android.content.res.ColorStateList.valueOf(color));p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33577385));return p;}
 private static int percent(String value){try{return Math.max(0,Math.min(100,Math.round(Float.parseFloat(value.replace("%","")))));}catch(Exception e){return 0;}}
 private ImageView poster(){ImageView v=new ImageView(getContext());v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setBackground(card(0xaa112333,10));v.setClipToOutline(true);v.setElevation(dp(8));return v;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
 private TextView label(String value,int size,boolean bold){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size*textScale);v.setTextColor(ink);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);Typeface original=RxOriginalAssets.font(getContext());
  if(original!=null&&prefs.getBoolean("arabic",true))
   v.setTypeface(original,bold?Typeface.BOLD:Typeface.NORMAL);
  else if(bold)v.setTypeface(null,Typeface.BOLD);
  return v;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
