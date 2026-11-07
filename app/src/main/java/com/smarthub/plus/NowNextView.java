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
 private final LinearLayout providerPanel,resolutionStrip;
 private final TextView[] resolutionChips=new TextView[5];private final TextView[] tigerChips=new TextView[6];private final LinearLayout posterTray;
 private final ProgressBar progress,strengthBar,qualityBar;
 private final android.content.SharedPreferences prefs;
 private final int ink=0xfff1f1f5,muted=0xffafbdc9;
 private boolean compact;private float textScale=1;private int accent=0xffdc49ec;private MatchTickerView ticker;
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
  int barH=detailed?Math.max(dp(174),OsdGeometry.bar(context)):
   Math.max(dp(164),Math.min(dp(188),OsdGeometry.bar(context)));
  int tickerH=prefs.getBoolean("sports_ticker",false)?dp(62):0;
  compact=barH<dp(145);
  textScale=Math.max(.90f,Math.min(1.06f,barH/(150f*
       getResources().getDisplayMetrics().density)));
  int alpha=Math.max(70,Math.min(98,prefs.getInt("osd_opacity",94)))*255/100;
  LinearLayout bar=column();bar.setGravity(Gravity.CENTER_VERTICAL);
  bar.setPadding(dp(20),dp(9),dp(20),dp(9));
  bar.setBackground(card(Color.argb(alpha,5,5,7),10));
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
  heading.setTextColor(0xff4cde4a);programme.addView(heading);
  LinearLayout icons=new LinearLayout(context);icons.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  String[] names={"HD","H265","UHD","TXT","SUB","EPG"};
  for(int i=0;i<names.length;i++){
   TextView chip=label(names[i],9,true);
   chip.setGravity(Gravity.CENTER);chip.setPadding(dp(4),dp(2),dp(4),dp(2));
   LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(20),1);
   ip.rightMargin=dp(3);icons.addView(chip,ip);tigerChips[i]=chip;
  }
  programme.addView(icons);
  nowLine=label("",compact?13:16,true);
  programme.addView(nowLine);
  nextLine=label("",compact?11:13,false);
  nextLine.setTextColor(0xffbac6d4);programme.addView(nextLine);
  progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
  progress.setMax(1000);
  progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffef524e));
  progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff344657));
  LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,dp(4));
  progressParams.topMargin=dp(6);programme.addView(progress,progressParams);
  remaining=label("",compact?9:11,false);
  remaining.setTextColor(accent);
  programme.addView(remaining);
  status=label("",10,false);status.setTextColor(muted);
  status.setVisibility(GONE);programme.addView(status);

  posterTray=new LinearLayout(context);
  posterTray.setGravity(Gravity.CENTER);
  posterTray.setPadding(dp(9),0,dp(9),0);
  primary.addView(posterTray,new LinearLayout.LayoutParams(dp(175),-2));
  posterTray.setVisibility(prefs.getBoolean("osd_tiger_expanded_posters",false)?VISIBLE:GONE);
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
  if(prefs.getBoolean("osd_swap_posters",false)){
   posterTray.removeView(left);posterTray.addView(left);
  }

  LinearLayout signal=column();signal.setGravity(Gravity.CENTER_VERTICAL);
  primary.addView(signal,new LinearLayout.LayoutParams(dp(compact?120:152),-2));
  clock=label("",compact?15:18,true);
  clock.setTextDirection(View.TEXT_DIRECTION_LTR);
  clock.setGravity(Gravity.RIGHT);signal.addView(clock);
  strengthLabel=label("S: —",10,false);
  strengthBar=meter(0xff20ccec);
  qualityLabel=label("Q: —",10,false);
  qualityBar=meter(0xffc233ed);
  signal.addView(strengthLabel);
  signal.addView(strengthBar,new LinearLayout.LayoutParams(-1,dp(4)));
  signal.addView(qualityLabel);
  signal.addView(qualityBar,new LinearLayout.LayoutParams(-1,dp(4)));

  // Bind targets for optional RX Details mode. All data sources and getters
  // remain intact; only their visibility changes.
  broadcast=label("",10,true);broadcast.setVisibility(GONE);
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
  LinearLayout details=new LinearLayout(context);
  details.setGravity(Gravity.CENTER_VERTICAL);
  details.addView(resolutionStrip);
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
  setAlpha(0f);animate().alpha(1f).setDuration(150).start();
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
  broadcast.setVisibility(GONE);
  String format=prefs.getString("native_track_format","");
  boolean nativeTrack=s.channelId.equals(prefs.getString("native_track_channel",""));
  boolean[] enabled={
   "HD".equalsIgnoreCase(badge)||"FHD".equalsIgnoreCase(badge),
   info.broadcast.toUpperCase(Locale.ROOT).contains("HEVC")||info.broadcast.toUpperCase(Locale.ROOT).contains("H265"),
   "UHD".equalsIgnoreCase(badge)||"4K".equalsIgnoreCase(badge),
   nativeTrack&&"TXT".equals(format),
   nativeTrack&&("TXT".equals(format)||"DVB".equals(format)),
   !s.title.isEmpty()||!s.nextTitle.isEmpty()
  };
  for(int i=0;i<tigerChips.length;i++){
   TextView chip=tigerChips[i];
   chip.setBackground(card(enabled[i]?0xffeeeeee:0xff343438,4));
   chip.setTextColor(enabled[i]?0xff101013:0xff74747b);
  }
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
  SimpleDateFormat day=new SimpleDateFormat("EEE dd MMM yyyy",prefs.getBoolean("arabic",true)?new Locale("ar"):Locale.ENGLISH);day.setTimeZone(TimeZone.getTimeZone("Asia/Baghdad"));clock.setText(shortTime(System.currentTimeMillis())+"\n"+day.format(new Date()));clock.setSingleLine(false);clock.setVisibility(prefs.getBoolean("osd_clock",true)?VISIBLE:GONE);
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
  details.add(info.signalDetail());technical.setText(String.join("   ·   ",details));technical.setVisibility(details.isEmpty()?GONE:VISIBLE);
  if(ticker!=null){ArrayList<SportsMatch> selected=new ArrayList<>();for(SportsMatch m:MatchRepository.get(getContext()).snapshot()){LeagueRepository.League l=LeagueRepository.byId(m.leagueId);if(l!=null&&MatchRepository.get(getContext()).selected(l)&&m.kickoff>System.currentTimeMillis()-86400000L&&m.kickoff<System.currentTimeMillis()+2*86400000L)selected.add(m);}ticker.bind(selected);}
  boolean show=prefs.getBoolean("osd_posters",true);currentTitle.setVisibility(s.title.isEmpty()?INVISIBLE:VISIBLE);nextTitle.setVisibility(s.nextTitle.isEmpty()?INVISIBLE:VISIBLE);currentPoster.setVisibility(s.title.isEmpty()||current==null||!show?INVISIBLE:VISIBLE);nextPoster.setVisibility(s.nextTitle.isEmpty()||next==null||!show?INVISIBLE:VISIBLE);
  ReceiverSignalReading lock=DirectReceiverSignal.reading(s);
  boolean noSignal=s.channelId.startsWith("DVB:")&&lock!=null&&lock.locked==0&&
    s.channelId.equals(prefs.getString("decoder_channel",""));
  setStatus(noSignal?tx("لا يوجد إشارة","NO SIGNAL"):message);
 }
 void expandedPosters(boolean expanded){
  posterTray.setVisibility(expanded?VISIBLE:GONE);
 }
 void metadata(MediaRepository.Item item){if(item!=null&&!item.id.startsWith("epg:")){String detail=item.release+(item.ratings.isEmpty()?"":" · ★ "+item.ratings);if(!detail.isEmpty()){remaining.setText(remaining.getText()+" · "+detail);remaining.setVisibility(VISIBLE);}if(prefs.getBoolean("osd_description",true)&&!item.summary.isEmpty())setStatus(item.summary);}}
 void provider(Bitmap image){providerLogo.setImageBitmap(image);providerLogo.setVisibility(image==null?GONE:VISIBLE);providerName.setVisibility(image==null?VISIBLE:GONE);}
 void setStatus(String value){
  String text=value==null?"":value.replace('\n',' ');
  status.setText(text);
  status.setTextColor(0xffef6c65);status.setVisibility((prefs.getBoolean("osd_rx_detailed",false)||text.equals("NO SIGNAL")||text.contains("لا يوجد إشارة"))&&!text.isEmpty()?VISIBLE:GONE);
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
