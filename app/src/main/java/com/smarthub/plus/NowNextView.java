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
 private final TextView broadcast,remaining,technical,weather,strengthLabel,qualityLabel,signalDetail,signalSource,encryptionLine,providerName,providerCaption;
 private final LinearLayout providerPanel;
 private final ProgressBar progress,strengthBar,qualityBar;
 private final android.content.SharedPreferences prefs;
 private final int ink=0xfff3f8ff,muted=0xffa6bfd0;
 private boolean compact,oemLayout;private float textScale=1;private int accent=0xffdc49ec;private MatchTickerView ticker;
 NowNextView(Context context){
  super(context);prefs=context.getSharedPreferences("smarthub",0);accent=0xffe84c58;try{accent=Color.parseColor(prefs.getString("osd_accent","#E84C58"));}catch(Exception ignored){}setLayoutDirection(View.LAYOUT_DIRECTION_LTR);int barH=OsdGeometry.bar(context),tickerH=prefs.getBoolean("sports_ticker",false)?dp(68):0;oemLayout=prefs.getBoolean("osd_oem_preset",false);compact=oemLayout||barH<dp(126);textScale=Math.max(.90f,Math.min(1.12f,barH/(150f*getResources().getDisplayMetrics().density)));
  LinearLayout left=column(),right=column();currentTitle=label(tx("الآن","NOW"),11,true);nextTitle=label(tx("التالي","NEXT"),11,true);currentTitle.setTextColor(0xfff7d97a);nextTitle.setTextColor(0xfff7d97a);left.addView(currentTitle);right.addView(nextTitle);currentPoster=poster();nextPoster=poster();left.addView(currentPoster,new LinearLayout.LayoutParams(dp(96),dp(142)));right.addView(nextPoster,new LinearLayout.LayoutParams(dp(96),dp(142)));LayoutParams lp=new LayoutParams(dp(96),-2,Gravity.LEFT|Gravity.BOTTOM);lp.setMargins(dp(22),0,0,barH+tickerH+dp(12));addView(left,lp);LayoutParams rp=new LayoutParams(dp(96),-2,Gravity.RIGHT|Gravity.BOTTOM);rp.setMargins(0,0,dp(22),barH+tickerH+dp(12));addView(right,rp);
  LinearLayout bar=column();bar.setPadding(dp(22),dp(compact?5:10),dp(22),dp(6));int alpha=Math.max(30,Math.min(95,prefs.getInt("osd_opacity",88)))*255/100;GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x18000000,(alpha<<24),0xf2000000});bar.setBackground(bg);LayoutParams bp=new LayoutParams(-1,barH,Gravity.BOTTOM);addView(bar,bp);
  LinearLayout head=new LinearLayout(context);head.setGravity(Gravity.CENTER_VERTICAL);heading=label("IRAQhub",compact?16:20,true);heading.setTextColor(0xff7be963);head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));broadcast=label("",10,true);broadcast.setGravity(Gravity.CENTER);LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(dp(60),dp(compact?17:21));badge.rightMargin=dp(18);head.addView(broadcast,badge);clock=label("",compact?13:16,true);clock.setGravity(Gravity.RIGHT);clock.setSingleLine(false);clock.setMaxLines(2);head.addView(clock);bar.addView(head);
  LinearLayout content=new LinearLayout(context);content.setGravity(Gravity.CENTER_VERTICAL);bar.addView(content,new LinearLayout.LayoutParams(-1,0,1));LinearLayout branding=column();branding.setGravity(Gravity.CENTER);logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);branding.addView(logo,new LinearLayout.LayoutParams(dp(compact?74:98),dp(compact?36:59)));content.addView(branding,new LinearLayout.LayoutParams(dp(compact?84:110),-2));
  LinearLayout lines=column();lines.setPadding(dp(14),0,dp(18),0);content.addView(lines,new LinearLayout.LayoutParams(0,-2,1));progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff505050));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(3));pp.bottomMargin=dp(compact?3:6);lines.addView(progress,pp);nowLine=label("",compact?11:14,false);lines.addView(nowLine);nextLine=label("",compact?10:13,false);nextLine.setTextColor(0xffc7cdd0);nextLine.setPadding(0,dp(3),0,0);lines.addView(nextLine);remaining=label("",compact?9:10,true);remaining.setTextColor(0xfff4d270);remaining.setPadding(0,dp(2),0,0);lines.addView(remaining);status=label("",10,false);status.setTextColor(muted);status.setVisibility(GONE);lines.addView(status);
  LinearLayout meters=column();content.addView(meters,new LinearLayout.LayoutParams(dp(compact?185:220),-2));LinearLayout sr=new LinearLayout(context);sr.setGravity(Gravity.CENTER_VERTICAL);strengthLabel=label("S: —",10,false);sr.addView(strengthLabel,new LinearLayout.LayoutParams(dp(47),-2));strengthBar=meter(0xff39cbe9);sr.addView(strengthBar,new LinearLayout.LayoutParams(0,dp(4),1));meters.addView(sr);LinearLayout qr=new LinearLayout(context);qr.setGravity(Gravity.CENTER_VERTICAL);qualityLabel=label("Q: —",10,false);qr.addView(qualityLabel,new LinearLayout.LayoutParams(dp(47),-2));qualityBar=meter(0xffcf31ce);qr.addView(qualityBar,new LinearLayout.LayoutParams(0,dp(4),1));meters.addView(qr);signalSource=label("",8,false);signalSource.setTextColor(muted);meters.addView(signalSource);weather=label("",10,false);weather.setSingleLine(false);weather.setMaxLines(2);weather.setTextColor(muted);meters.addView(weather);
  providerPanel=column();providerPanel.setGravity(Gravity.CENTER);providerPanel.setPadding(dp(12),0,0,0);content.addView(providerPanel,new LinearLayout.LayoutParams(dp(compact?68:85),-2));providerCaption=label(tx("المزود","PROVIDER"),8,false);providerCaption.setTextColor(0xffe5cd7d);providerCaption.setGravity(Gravity.CENTER);providerPanel.addView(providerCaption);providerLogo=new ImageView(context);providerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);providerPanel.addView(providerLogo,new LinearLayout.LayoutParams(dp(compact?36:44),dp(compact?19:25)));providerLogo.setVisibility(GONE);providerName=label("",12,true);providerName.setSingleLine(false);providerName.setMaxLines(2);providerName.setGravity(Gravity.CENTER);providerPanel.addView(providerName);
  LinearLayout bottom=new LinearLayout(context);bottom.setGravity(Gravity.CENTER_VERTICAL);bar.addView(bottom);technical=label("",compact?8:10,false);technical.setTextColor(0xffc4cbd0);bottom.addView(technical,new LinearLayout.LayoutParams(0,-2,1));signalDetail=label("",compact?8:10,false);signalDetail.setTextDirection(View.TEXT_DIRECTION_LTR);signalDetail.setGravity(Gravity.CENTER);bottom.addView(signalDetail,new LinearLayout.LayoutParams(dp(compact?185:220),-2));encryptionLine=label("",compact?8:10,true);encryptionLine.setTextColor(0xffd9db86);encryptionLine.setGravity(Gravity.RIGHT);bottom.addView(encryptionLine,new LinearLayout.LayoutParams(dp(compact?68:85),-2));
  if(tickerH>0){ticker=new MatchTickerView(context);LayoutParams tick=new LayoutParams(-1,tickerH,Gravity.BOTTOM);tick.setMargins(dp(10),0,dp(10),barH);addView(ticker,tick);}if(prefs.getBoolean("osd_swap_posters",false)){((LayoutParams)left.getLayoutParams()).gravity=Gravity.RIGHT|Gravity.BOTTOM;((LayoutParams)right.getLayoutParams()).gravity=Gravity.LEFT|Gravity.BOTTOM;}setAlpha(0);animate().alpha(1).setDuration(180).start();
 }

 void bind(SatelliteState s,Bitmap current,Bitmap next,Bitmap channelLogo,String message){
  ReceiverInfoRepository.Info info=ReceiverInfoRepository.get(getContext()).info(s);
  String badge=info.badge;broadcast.setText(badge);broadcast.setBackgroundResource(ResolutionBadge.asset(badge));broadcast.setTextColor(ResolutionBadge.color(badge));broadcast.setVisibility(badge.isEmpty()?GONE:VISIBLE);
  currentPoster.setImageBitmap(current);nextPoster.setImageBitmap(next);
  if(current==null)currentPoster.setImageDrawable(null);if(next==null)nextPoster.setImageDrawable(null);currentPoster.setAlpha(current==null?.2f:1);nextPoster.setAlpha(next==null?.2f:1);
  currentPoster.setContentDescription(tx("الآن · ","NOW · ")+s.title);nextPoster.setContentDescription(tx("التالي · ","NEXT · ")+s.nextTitle);currentTitle.setText(tx("الآن · ","NOW · ")+shortTime(s.startMs));nextTitle.setText(tx("التالي · ","NEXT · ")+shortTime(s.nextStartMs));
  String number=s.channelId.replaceFirst("^Aurum:","");if(s.channelId.startsWith("DVB:")){long id=prefs.getLong("own_dvb_channel",-1);number=id>=0?Long.toString(id+1):"";}if(!number.matches("[0-9]+"))number="";
  heading.setText((number.isEmpty()?"":number+"  ·  ")+(s.channelName.isEmpty()?"IRAQhub":s.channelName));
  String provider=info.provider;providerName.setText(provider.isEmpty()?"":provider.replace(" (CHL)",""));providerCaption.setText(tx("المزود","PROVIDER")+(provider.endsWith(" (CHL)")?" · CHL":""));providerPanel.setContentDescription(tx("مزود الخدمة: ","Service provider: ")+provider);
  nowLine.setText(tx("الآن  ","NOW  ")+(s.title.isEmpty()?tx("بيانات البرنامج غير متاحة","Programme unavailable"):s.title)+range(s.startMs,s.endMs));
  nextLine.setText(tx("التالي  ","NEXT  ")+(s.nextTitle.isEmpty()?tx("غير متاح من EPG","Unavailable from EPG"):s.nextTitle)+range(s.nextStartMs,s.nextEndMs));
  if(channelLogo==null)logo.setImageResource(R.drawable.channel_placeholder);else logo.setImageBitmap(channelLogo);logo.setContentDescription("Channel logo · "+s.channelName);
  long length=s.endMs-s.startMs;progress.setProgress(length>0?(int)Math.max(0,Math.min(1000,(System.currentTimeMillis()-s.startMs)*1000/length)):0);progress.setVisibility(length>0?VISIBLE:INVISIBLE);
  ProgrammeClock timer=new ProgrammeClock(s.startMs,s.endMs,System.currentTimeMillis());remaining.setText(timer.known?tx("متبقي  ","REMAINING  ")+ProgrammeClock.format(timer.remainingMs,false):"");remaining.setVisibility(timer.known?VISIBLE:GONE);
  SimpleDateFormat day=new SimpleDateFormat("EEE dd MMM yyyy",prefs.getBoolean("arabic",true)?new Locale("ar"):Locale.ENGLISH);day.setTimeZone(TimeZone.getTimeZone("Asia/Baghdad"));clock.setText(shortTime(System.currentTimeMillis())+"\n"+day.format(new Date()));clock.setVisibility(prefs.getBoolean("osd_clock",true)?VISIBLE:GONE);
  weather.setText(WeatherRepository.label(getContext()));weather.setVisibility(!compact&&prefs.getBoolean("osd_weather",false)?VISIBLE:GONE);
  String strength=ReceptionData.value(getContext(),s,"strength"),quality=ReceptionData.value(getContext(),s,"quality");strengthLabel.setText("S: "+(strength.isEmpty()?"—":strength));qualityLabel.setText("Q: "+(quality.isEmpty()?"—":quality));strengthBar.setProgress(percent(strength),true);qualityBar.setProgress(percent(quality),true);strengthBar.setVisibility(strength.isEmpty()?INVISIBLE:VISIBLE);qualityBar.setVisibility(quality.isEmpty()?INVISIBLE:VISIBLE);
  signalDetail.setText(info.signalDetail());signalSource.setText(s.source.startsWith("IPTV")?"IPTV":DirectReceiverSignal.sourceLabel(s,prefs.getBoolean("arabic",true)));signalSource.setVisibility(compact?GONE:VISIBLE);
  encryptionLine.setText(info.encryption.isEmpty()?tx("التشفير: —","Encryption: —"):info.encryption);encryptionLine.setContentDescription("encryption-status");
  ArrayList<String> details=new ArrayList<>();if(!info.broadcast.isEmpty())details.add(info.broadcast);
  if(s.source.startsWith("IPTV")){
    String audio=prefs.getString("osd_audio","");
    if(!audio.isEmpty())details.add(audio);
    if(prefs.getBoolean("decoder_input_profile422",false))
      details.add("AVC High 4:2:2 · "+tx("ملف فيديو فقط","video profile only"));
    else if(prefs.getBoolean("decoder_422_possible",false))
      details.add("HEVC RExt · "+tx("صيغة اللون غير مؤكدة","chroma unknown"));
  }else if(s.channelId.startsWith("DVB:")&&prefs.getBoolean("dvb_422_compat_active",false)){
    details.add(tx("محاولة تشغيل 4:2:2 · غير مؤكدة","4:2:2 compatibility attempt · unverified"));
  }
  if(s.channelId.equals(prefs.getString("subtitle_channel_id",""))&&s.startMs==prefs.getLong("subtitle_event_start_ms",0)&&prefs.getBoolean("overlay_caption",false))details.add("SUB · "+prefs.getString("subtitle_provider","")+String.format(Locale.ROOT," %+.3f s",prefs.getLong("subtitle_offset",0)/1000.0));
  SportsMatch related=MatchRepository.get(getContext()).forProgramme(s);if(related!=null)details.add(related.league+" · "+related.home+" "+related.score()+" "+related.away+" · "+related.state());
  technical.setText(String.join("   ·   ",details));technical.setVisibility(details.isEmpty()?GONE:VISIBLE);
  if(ticker!=null){ArrayList<SportsMatch> selected=new ArrayList<>();for(SportsMatch m:MatchRepository.get(getContext()).snapshot()){LeagueRepository.League l=LeagueRepository.byId(m.leagueId);if(l!=null&&MatchRepository.get(getContext()).selected(l)&&m.kickoff>System.currentTimeMillis()-86400000L&&m.kickoff<System.currentTimeMillis()+2*86400000L)selected.add(m);}ticker.bind(selected);}
  boolean show=prefs.getBoolean("osd_posters",true)&&!oemLayout;currentTitle.setVisibility(oemLayout?GONE:s.title.isEmpty()?INVISIBLE:VISIBLE);nextTitle.setVisibility(oemLayout?GONE:s.nextTitle.isEmpty()?INVISIBLE:VISIBLE);currentPoster.setVisibility(s.title.isEmpty()||current==null||!show?INVISIBLE:VISIBLE);nextPoster.setVisibility(s.nextTitle.isEmpty()||next==null||!show?INVISIBLE:VISIBLE);setStatus(message);
 }
 void metadata(MediaRepository.Item item){if(item!=null&&!item.id.startsWith("epg:")){String detail=item.release+(item.ratings.isEmpty()?"":" · ★ "+item.ratings);if(!detail.isEmpty()){remaining.setText(remaining.getText()+" · "+detail);remaining.setVisibility(VISIBLE);}if(prefs.getBoolean("osd_description",true)&&!item.summary.isEmpty())setStatus(item.summary);}}
 void provider(Bitmap image){providerLogo.setImageBitmap(image);providerLogo.setVisibility(image==null?GONE:VISIBLE);providerName.setVisibility(image==null?VISIBLE:GONE);}
 void setStatus(String value){String text=value==null?"":value.replace('\n',' ');status.setText(text);status.setVisibility(compact||text.isEmpty()?GONE:VISIBLE);}
 private String tx(String ar,String en){return prefs.getBoolean("arabic",true)?ar:en;}
 private String range(long a,long b){return a>0?"  ·  "+shortTime(a)+(b>a?" – "+shortTime(b):""):"";}
 private static String shortTime(long at){if(at<=0)return "";SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("Asia/Baghdad"));return f.format(new Date(at));}
 private void divider(LinearLayout parent){View v=new View(getContext());v.setBackgroundColor(0x337da4ba);parent.addView(v,new LinearLayout.LayoutParams(dp(1),dp(84)));}
 private GradientDrawable card(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private ProgressBar meter(int color){ProgressBar p=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setProgressTintList(android.content.res.ColorStateList.valueOf(color));p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33577385));return p;}
 private static int percent(String value){try{return Math.max(0,Math.min(100,Math.round(Float.parseFloat(value.replace("%","")))));}catch(Exception e){return 0;}}
 private ImageView poster(){ImageView v=new ImageView(getContext());v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setBackground(card(0xaa112333,10));v.setClipToOutline(true);v.setElevation(dp(8));return v;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
 private TextView label(String value,int size,boolean bold){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size*textScale);v.setTextColor(ink);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
