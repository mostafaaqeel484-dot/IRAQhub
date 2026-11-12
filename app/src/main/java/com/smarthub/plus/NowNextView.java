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
 private final ImageView countryFlag;
 private String displayedFlagCode="";
 private String selectedProviderBrand="";
 private Bitmap displayedChannelLogo;
 private String displayedLogoStyleAtBitmap="";
 private boolean suppressDuplicateProvider;

 private static Bitmap withoutWhiteBorder(Bitmap source){
  int w=source.getWidth(),h=source.getHeight();
  if(w<2||h<2||w>1024||h>1024||source.getConfig()==Bitmap.Config.HARDWARE)return source;
  int[] pixels=new int[w*h];
  source.getPixels(pixels,0,w,0,0,w,h);
  int corners=0;
  for(int i:new int[]{0,w-1,(h-1)*w,w*h-1})if(isBackdropWhite(pixels[i]))corners++;
  if(corners<3)return source;
  java.util.BitSet visited=new java.util.BitSet(pixels.length);
  int[] queue=new int[pixels.length];
  int tail=0;
  for(int i=0;i<pixels.length;i++){
   if((i<w||i>=pixels.length-w||i%w==0||i%w==w-1)&&isBackdropWhite(pixels[i])){
    visited.set(i);
    queue[tail++]=i;
   }
  }
  for(int head=0;head<tail;head++){
   int i=queue[head],x=i%w;
   for(int direction=0;direction<4;direction++){
    if((direction==0&&x==0)||(direction==1&&x==w-1))continue;
    int next=i+(direction==0?-1:direction==1?1:direction==2?-w:w);
    if(next<0||next>=pixels.length||visited.get(next)||!isBackdropWhite(pixels[next]))continue;
    visited.set(next);
    queue[tail++]=next;
   }
  }
  if(tail==0||tail==pixels.length)return source;
  for(int i=0;i<tail;i++)pixels[queue[i]]=0;
  // Crop only the newly transparent outer matte. Keep all interior white
  // lettering and the original logo aspect ratio intact.
  int minX=w,minY=h,maxX=-1,maxY=-1;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   if((pixels[y*w+x]>>>24)>8){
    minX=Math.min(minX,x);minY=Math.min(minY,y);
    maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
   }
  }
  if(maxX<minX||maxY<minY)return source;
  int pad=Math.max(2,Math.min(w,h)/40);
  minX=Math.max(0,minX-pad);minY=Math.max(0,minY-pad);
  maxX=Math.min(w-1,maxX+pad);maxY=Math.min(h-1,maxY+pad);
  if(minX==0&&minY==0&&maxX==w-1&&maxY==h-1)
   return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
  int cropW=maxX-minX+1,cropH=maxY-minY+1;
  int[] cropped=new int[cropW*cropH];
  for(int y=0;y<cropH;y++)
   System.arraycopy(pixels,(minY+y)*w+minX,cropped,y*cropW,cropW);
  return Bitmap.createBitmap(cropped,cropW,cropH,Bitmap.Config.ARGB_8888);
 }

 private static boolean isBackdropWhite(int pixel){
  int a=(pixel>>>24)&255,r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
  int low=Math.min(r,Math.min(g,b)),high=Math.max(r,Math.max(g,b));
  // Sky Cinema and similar supplier art often carries an ivory matte.
  // Flood-fill only edge-connected pale pixels: do not erase internal text.
  return a>=245&&low>=218&&high-low<=40;
 }
 private final TextView currentTitle,nextTitle,currentWorkName,nextWorkName,currentSynopsis,nextSynopsis,heading,nowLine,nextLine,status,clock,year;
 private final TextView broadcast,remaining,technical,weather,strengthLabel,qualityLabel,encryptionLine,providerName,providerCaption;
 private final LinearLayout providerPanel,resolutionStrip,details,bar,leftPosterBox,rightPosterBox;
 private final TextView[] resolutionChips=new TextView[5];
 private final TextView[] featureChips=new TextView[6];
 private final TextView[] caChips=new TextView[CaBadgePolicy.LABELS.length];
 private final int[] caDrawn=new int[CaBadgePolicy.LABELS.length];
 private final int[] resolutionDrawn=new int[5],featureDrawn=new int[6];
 private int encryptionDrawn=-1;
 private final ProgressBar progress,strengthBar,qualityBar;
 private final android.content.SharedPreferences prefs;
 private final int ink=0xfff1f1f5,muted=0xffafbdc9;
 private boolean compact,expanded,liteApplied,lastLiteValue;private float textScale=1;private int accent=0xffdc49ec;private MatchTickerView ticker;
 NowNextView(Context context){
  super(context);
  prefs=context.getSharedPreferences("smarthub",0);
  java.util.Arrays.fill(resolutionDrawn,-1);
  java.util.Arrays.fill(featureDrawn,-1);
  java.util.Arrays.fill(caDrawn,-1);
  accent=RxSkin.accent(context);
  try{accent=Color.parseColor(prefs.getString("osd_accent",
      String.format(Locale.ROOT,"#%06X",accent&0xffffff)));}catch(Exception ignored){}
  setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  // 4.0.1 reference: black bottom ribbon; date/year right with all icons below.
  // Programme posters are independently placed ABOVE the black ribbon.
  final boolean detailed=prefs.getBoolean("osd_rx_detailed",false);
  int barH=dp(TigerOsdMetrics.barHeightDp(detailed));
  int tickerH=prefs.getBoolean("sports_ticker",false)?dp(62):0;
  compact=TigerOsdMetrics.isCompact(detailed);
  textScale=compact?.96f:1.02f;
  int alpha=Math.max(30,Math.min(95,prefs.getInt("osd_opacity",88)))*255/100;
  bar=column();bar.setGravity(Gravity.CENTER_VERTICAL);
  bar.setPadding(dp(20),dp(8),dp(20),dp(7));
  GradientDrawable dark=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
   new int[]{Color.argb(alpha,0,0,0),Color.argb(Math.min(255,alpha+12),6,6,8)});
  dark.setCornerRadius(0);bar.setBackground(dark);
  bar.setElevation(0);
  LayoutParams barPosition=new LayoutParams(-1,barH,Gravity.BOTTOM);
  barPosition.setMargins(0,0,0,0);
  addView(bar,barPosition);

  // Reference: channel left, small year + day/date/time at right; icons BELOW date.
  LinearLayout header=new LinearLayout(context);
  header.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout headRight=column();headRight.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
  bar.addView(header,new LinearLayout.LayoutParams(-1,dp(TigerOsdMetrics.headerHeightDp())));
  // The former weighted but empty headLeft and 2nd full-height primary row
  // created the giant blank panel. All EPG content belongs in the header.
  final boolean bottomSignal=SignalPlacement.bottom(
      prefs.getString(SignalPlacement.KEY,SignalPlacement.DEFAULT));
  LinearLayout brand=column();brand.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  logo.setBackgroundColor(android.graphics.Color.TRANSPARENT);
  // The receiver logo fills most of the top-left column. FIT_CENTER preserves
  // the exact aspect ratio and keeps long DVB channel names readable below.
  brand.addView(logo,new LinearLayout.LayoutParams(dp(TigerOsdMetrics.channelLogoWidthDp()),
    dp(TigerOsdMetrics.channelLogoHeightDp())));
  // Render a bundled real national flag; never a platform-dependent emoji.
  countryFlag=new ImageView(context);
  countryFlag.setScaleType(ImageView.ScaleType.FIT_CENTER);
  countryFlag.setBackgroundColor(Color.TRANSPARENT);
  countryFlag.setVisibility(GONE);
  providerPanel=column();providerPanel.setGravity(Gravity.CENTER);
  providerCaption=label(tx("المزود","PROVIDER"),9,false);
  providerCaption.setTextColor(muted);
  providerCaption.setVisibility(GONE);
  providerLogo=new ImageView(context);
  providerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  providerLogo.setVisibility(GONE);
  providerName=label("",10,false);providerName.setGravity(Gravity.CENTER);
  providerName.setSingleLine(true);providerName.setEllipsize(TextUtils.TruncateAt.END);
  providerName.setTextColor(muted);
  providerPanel.addView(providerCaption);
  providerPanel.addView(providerLogo,new LinearLayout.LayoutParams(dp(96),dp(42)));
  providerPanel.addView(providerName);
  providerPanel.setVisibility(GONE);
  // Channel identity belongs in the top header, not at the bottom of the
  // programme row. This keeps the CHMAX logo aligned with the channel name.
  LinearLayout.LayoutParams brandPosition=new LinearLayout.LayoutParams(dp(194),-1);
  brandPosition.rightMargin=dp(8);
  header.addView(brand,0,brandPosition);

  LinearLayout programme=column();
  programme.setGravity(Gravity.CENTER_VERTICAL);
  programme.setPadding(dp(12),0,dp(12),0);
  header.addView(programme,new LinearLayout.LayoutParams(0,-1,1f));
  heading=label("IRAQhub",compact?13:14,true);
  heading.setGravity(Gravity.CENTER);
  heading.setIncludeFontPadding(false);
  heading.setTextColor(0xff3fe44d);
  // Keep the original RX Skin and 81dp header. The image no longer overlaps
  // the title: show two lines instead of clipping long DVB names.
  heading.setSingleLine(true);heading.setMaxLines(1);
  heading.setEllipsize(TextUtils.TruncateAt.END);
  heading.setAutoSizeTextTypeUniformWithConfiguration(9,12,1,
    android.util.TypedValue.COMPLEX_UNIT_SP);
  brand.addView(heading,new LinearLayout.LayoutParams(-1,dp(TigerOsdMetrics.channelNameHeightDp())));
  brand.addView(countryFlag,new LinearLayout.LayoutParams(dp(TigerOsdMetrics.channelFlagWidthDp()),
    dp(TigerOsdMetrics.channelFlagHeightDp())));
  broadcast=label("",10,false);broadcast.setTextColor(0xffe0e0e0);
  // No empty headLeft column; broadcasting information is in the technical footer.
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

  // Independent left/right NOW/NEXT cards stay visible alongside the bottom
  // InfoBar. Image, programme name and synopsis are one coherent TV card.
  // Never move the video surface or cover the channel/date ribbon.
  LinearLayout left=column();left.setGravity(Gravity.CENTER_HORIZONTAL);
  left.setPadding(dp(7),dp(6),dp(7),dp(7));
  left.setBackground(card(0xeb09131e,9));
  currentTitle=label(tx("الآن","NOW"),10,true);
  currentTitle.setGravity(Gravity.CENTER);currentTitle.setTextColor(accent);
  currentPoster=poster();
  currentWorkName=label("",11,true);
  currentWorkName.setSingleLine(false);currentWorkName.setMaxLines(2);
  currentWorkName.setGravity(Gravity.CENTER);currentWorkName.setTextColor(ink);
  currentSynopsis=label("",10,false);
  currentSynopsis.setSingleLine(false);currentSynopsis.setMaxLines(3);
  currentSynopsis.setEllipsize(TextUtils.TruncateAt.END);
  currentSynopsis.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  currentSynopsis.setTextColor(0xffc1ccd7);
  left.addView(currentTitle);
  left.addView(currentPoster,new LinearLayout.LayoutParams(dp(108),dp(143)));
  left.addView(currentWorkName,new LinearLayout.LayoutParams(-1,-2));
  left.addView(currentSynopsis,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout right=column();right.setGravity(Gravity.CENTER_HORIZONTAL);
  right.setPadding(dp(7),dp(6),dp(7),dp(7));
  right.setBackground(card(0xeb09131e,9));
  nextTitle=label(tx("التالي","NEXT"),10,true);
  nextTitle.setGravity(Gravity.CENTER);nextTitle.setTextColor(0xffd3d7db);
  nextPoster=poster();
  nextWorkName=label("",11,true);
  nextWorkName.setSingleLine(false);nextWorkName.setMaxLines(2);
  nextWorkName.setGravity(Gravity.CENTER);nextWorkName.setTextColor(ink);
  nextSynopsis=label("",10,false);
  nextSynopsis.setSingleLine(false);nextSynopsis.setMaxLines(3);
  nextSynopsis.setEllipsize(TextUtils.TruncateAt.END);
  nextSynopsis.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  nextSynopsis.setTextColor(0xffc1ccd7);
  right.addView(nextTitle);
  right.addView(nextPoster,new LinearLayout.LayoutParams(dp(108),dp(143)));
  right.addView(nextWorkName,new LinearLayout.LayoutParams(-1,-2));
  right.addView(nextSynopsis,new LinearLayout.LayoutParams(-1,-2));
  leftPosterBox=left;rightPosterBox=right;
  boolean swap=prefs.getBoolean("osd_swap_posters",false);
  LayoutParams leftPosition=new LayoutParams(dp(184),-2,Gravity.START|Gravity.BOTTOM);
  leftPosition.setMargins(dp(12),0,0,barH+dp(12));
  LayoutParams rightPosition=new LayoutParams(dp(184),-2,Gravity.END|Gravity.BOTTOM);
  rightPosition.setMargins(0,0,dp(12),barH+dp(12));
  addView(swap?right:left,leftPosition);
  addView(swap?left:right,rightPosition);
  leftPosterBox.setVisibility(GONE);rightPosterBox.setVisibility(GONE);

  LinearLayout signal=new LinearLayout(context);
  signal.setOrientation(bottomSignal?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
  signal.setGravity(Gravity.CENTER_VERTICAL);
  if(bottomSignal){
    signal.setPadding(dp(12),0,dp(12),0);
    bar.addView(signal,new LinearLayout.LayoutParams(-1,dp(25)));
  }else{
    header.addView(signal,new LinearLayout.LayoutParams(dp(148),-1));
  }
  header.addView(headRight,new LinearLayout.LayoutParams(dp(250),-1));
  LinearLayout providerAndDate=new LinearLayout(context);
  providerAndDate.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
  LinearLayout dateBlock=column();dateBlock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
  LinearLayout.LayoutParams providerAtDate=new LinearLayout.LayoutParams(dp(98),-2);
  providerAtDate.rightMargin=dp(9);
  providerAndDate.addView(providerPanel,providerAtDate);
  year=label("",10,false);year.setTextColor(0xffb8c0c7);
  year.setGravity(Gravity.RIGHT);dateBlock.addView(year);
  clock=label("",compact?13:16,true);
  clock.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
  clock.setGravity(Gravity.RIGHT);dateBlock.addView(clock);
  providerAndDate.addView(dateBlock,new LinearLayout.LayoutParams(-2,-2));
  headRight.addView(providerAndDate,new LinearLayout.LayoutParams(-1,-2));
  strengthLabel=label("S: —",10,false);
  strengthBar=meter(0xff28cbfa);
  qualityLabel=label("Q: —",10,false);
  qualityBar=meter(0xffcb27e6);
  if(bottomSignal){
    signal.addView(strengthLabel,new LinearLayout.LayoutParams(dp(56),-2));
    LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(0,dp(4),1f);
    s.rightMargin=dp(18);signal.addView(strengthBar,s);
    signal.addView(qualityLabel,new LinearLayout.LayoutParams(dp(56),-2));
    signal.addView(qualityBar,new LinearLayout.LayoutParams(0,dp(4),1f));
  }else{
    strengthLabel.setTextSize(9);
    qualityLabel.setTextSize(9);
    signal.addView(strengthLabel);
    signal.addView(strengthBar,new LinearLayout.LayoutParams(-1,dp(3)));
    signal.addView(qualityLabel);
    signal.addView(qualityBar,new LinearLayout.LayoutParams(-1,dp(3)));
  }

  // Bind targets for optional RX Details mode. All data sources and getters
  // remain intact; only their visibility changes.
  
  resolutionStrip=new LinearLayout(context);
  resolutionStrip.setGravity(Gravity.CENTER_VERTICAL);
  for(int i=0;i<resolutionChips.length;i++){
   TextView chip=label(ReceiverTechnicalBadges.RESOLUTIONS[i],10,true);
   chip.setGravity(Gravity.CENTER);chip.setPadding(dp(3),0,dp(3),0);
   resolutionChips[i]=chip;
   resolutionStrip.addView(chip,new LinearLayout.LayoutParams(dp(TigerOsdMetrics.iconWidthDp()),dp(TigerOsdMetrics.iconHeightDp())));
  }
  weather=label("",10,false);weather.setTextColor(muted);
  encryptionLine=label("",10,true);encryptionLine.setTextColor(accent);
  technical=label("",10,false);technical.setTextColor(muted);
  details=new LinearLayout(context);
  details.setGravity(Gravity.CENTER_VERTICAL);
  // Two uniform badge rows occupy the centre, as on the original receiver.
  LinearLayout featureRow=new LinearLayout(context);featureRow.setGravity(Gravity.CENTER_VERTICAL);
  String[] tags={"TXT","H265","SUB","EPG","CA","PIP"};
  for(int i=0;i<tags.length;i++){
   TextView c=label(tags[i],9,true);c.setGravity(Gravity.CENTER);c.setPadding(dp(3),dp(1),dp(3),dp(1));
   featureChips[i]=c;featureRow.addView(c,new LinearLayout.LayoutParams(dp(TigerOsdMetrics.iconWidthDp()),dp(TigerOsdMetrics.iconHeightDp())));
  }
  featureRow.setGravity(Gravity.RIGHT);
  resolutionStrip.setGravity(Gravity.RIGHT);
  headRight.addView(featureRow);
  headRight.addView(resolutionStrip);
  details.addView(weather);
  details.addView(encryptionLine);
  // Expanded-only diagnostics must not crowd the compact reference ribbon.
  details.setVisibility(detailed?VISIBLE:GONE);
  bar.addView(details,new LinearLayout.LayoutParams(-1,-2));

  // Reference bottom row: BER/SNR/aspect/bitrate at left and CA/FTA at right.
  LinearLayout footer=new LinearLayout(context);
  footer.setGravity(Gravity.CENTER_VERTICAL);
  technical.setSingleLine(true);
  technical.setEllipsize(android.text.TextUtils.TruncateAt.END);
  technical.setTextColor(0xffd7dadd);
  LinearLayout caStrip=new LinearLayout(context);
  caStrip.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  for(int i=0;i<caChips.length;i++){
   TextView chip=label(CaBadgePolicy.LABELS[i],9,true);
   chip.setGravity(Gravity.CENTER);
   chip.setPadding(dp(3),0,dp(3),0);
   caChips[i]=chip;
   int w=i==3?dp(76):i==4?dp(43):dp(41);
   LinearLayout.LayoutParams caParams=new LinearLayout.LayoutParams(w,dp(20));
   caParams.rightMargin=dp(2);
   caStrip.addView(chip,caParams);
  }
  footer.addView(caStrip,new LinearLayout.LayoutParams(-2,dp(20)));
  technical.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
  footer.addView(technical,new LinearLayout.LayoutParams(0,-1,1));
  bar.addView(footer,new LinearLayout.LayoutParams(-1,dp(22)));

  if(tickerH>0){
   ticker=new MatchTickerView(context);
   LayoutParams tick=new LayoutParams(-1,tickerH,Gravity.BOTTOM);
   // Keep the sports ticker in the middle, clear of both poster columns.
   tick.setMargins(dp(140),0,dp(140),barH+dp(8));
   addView(ticker,tick);
  }
  setExpanded(detailed);
  if(PlaybackResourceMode.active())setAlpha(1f);
  else{setAlpha(0f);animate().alpha(1f).setDuration(150).start();}
 }

 void setExpanded(boolean value){
  expanded=value;
  updatePosterVisibility();
  // Keep poster corners just above the expanded/compact bar.
  int top=dp(TigerOsdMetrics.posterBottomMarginDp(value));
  FrameLayout.LayoutParams left=(FrameLayout.LayoutParams)leftPosterBox.getLayoutParams();
  FrameLayout.LayoutParams right=(FrameLayout.LayoutParams)rightPosterBox.getLayoutParams();
  if(left!=null&&left.bottomMargin!=top){left.bottomMargin=top;leftPosterBox.setLayoutParams(left);}
  if(right!=null&&right.bottomMargin!=top){right.bottomMargin=top;rightPosterBox.setLayoutParams(right);}
  details.setVisibility(value?VISIBLE:GONE);
  FrameLayout.LayoutParams position=(FrameLayout.LayoutParams)bar.getLayoutParams();
  if(position!=null){position.height=dp(TigerOsdMetrics.barHeightDp(value));bar.setLayoutParams(position);}
 }
 void bind(SatelliteState s,Bitmap current,Bitmap next,Bitmap channelLogo,String message){
  boolean lite=PlaybackResourceMode.lite(getContext());
  // Verified <=512px cached artwork is safe to display during 4:2:2.
  // Network lookups are budgeted by LocalInfoBarController; never blank
  // already-cached programme images merely because the decoder is active.
  if(!liteApplied||lite!=lastLiteValue){
   bar.setElevation(lite?0:dp(6));
   currentPoster.setElevation(lite?0:dp(8));
   nextPoster.setElevation(lite?0:dp(8));
   liteApplied=true;lastLiteValue=lite;
  }
  ReceiverInfoRepository.Info info=ReceiverInfoRepository.get(getContext()).info(s);
  String badge=info.badge;
  // Inactive standards are visible but muted. Only the verified resolution is gold.
  for(int i=0;i<resolutionChips.length;i++){
   String kind=ReceiverTechnicalBadges.RESOLUTIONS[i];
   boolean active=kind.equals(badge);
   TextView chip=resolutionChips[i];
   int state=active?1:0;
   if(resolutionDrawn[i]!=state){
    resolutionDrawn[i]=state;
    chip.setTextColor(active?0xff081322:0xff9caebd);
    GradientDrawable background=new GradientDrawable();
    background.setColor(active?accent:0xa0263749);
    background.setStroke(dp(1),active?0xfff1f5f5:0x66899caf);
    background.setCornerRadius(dp(4));
    chip.setBackground(background);
    chip.setContentDescription(active?kind+" · "+tx("الدقة الفعلية","observed resolution"):kind+" · "+tx("غير نشط","inactive"));
   }
  }
  resolutionStrip.setContentDescription(badge.isEmpty()?tx("الدقة غير متاحة","Video resolution not available"):badge);
  // The transponder belongs in the channel-list footer, not below the channel title.
  broadcast.setText("");broadcast.setVisibility(GONE);
  currentPoster.setImageBitmap(current);nextPoster.setImageBitmap(next);
  if(current==null)currentPoster.setImageDrawable(null);if(next==null)nextPoster.setImageDrawable(null);currentPoster.setAlpha(current==null?.2f:1);nextPoster.setAlpha(next==null?.2f:1);
  currentPoster.setContentDescription(tx("الآن · ","NOW · ")+s.title);
  nextPoster.setContentDescription(tx("التالي · ","NEXT · ")+s.nextTitle);
  currentTitle.setText(tx("الآن · ","NOW · ")+shortTime(s.startMs));
  nextTitle.setText(tx("التالي · ","NEXT · ")+shortTime(s.nextStartMs));
  currentWorkName.setText(s.title);nextWorkName.setText(s.nextTitle);
  String number=s.channelId.replaceFirst("^Aurum:","");
  if(s.channelId.startsWith("DVB:")){
   int ordinal=prefs.getInt("own_dvb_sat_number",0);
   number=ordinal>0&&s.channelId.equals(prefs.getString("own_dvb_sat_number_channel",""))?
     Integer.toString(ordinal):"";
  }
  if(!number.matches("[0-9]+"))number="";
  heading.setText((number.isEmpty()?"":number+"  ")+(s.channelName.isEmpty()?"IRAQhub":s.channelName));
  String provider=info.provider;
   ProviderBrandArt branding=ProviderBrandArt.get(getContext());
   String official=branding.provider(provider,s.channelName,s.channelId);
   String shownProvider=official.isEmpty()?provider.replace(" (CHL)",""):official;
   providerName.setText(shownProvider);
   providerCaption.setText(tx("المزود","PROVIDER")+(provider.endsWith(" (CHL)")?" · CHL":""));
   providerPanel.setContentDescription(tx("مزود الخدمة: ","Service provider: ")+shownProvider);
   // Preserve real manufacturer artwork across the frequent OSD clock ticks.
   String brandIdentity=shownProvider+"|"+s.channelId+"|"+
     prefs.getLong(ProviderLogoOverrides.REVISION,0)+"|"+
     ChannelLogoSourcePrefs.providers(getContext());
   if(!brandIdentity.equals(selectedProviderBrand)){
    selectedProviderBrand=brandIdentity;
    Bitmap fallbackBrand=branding.badge(provider,s.channelName,s.channelId);
    providerLogo.setImageBitmap(fallbackBrand);
    providerLogo.setVisibility(fallbackBrand==null?GONE:VISIBLE);
   }
   String country=branding.country(s.channelName,s.channelId,provider);
   ChannelLogoAppearance.Config appearance=ChannelLogoAppearance.read(getContext(),s.channelId);
   String flagKey=appearance.flag?country:"";
   if(!flagKey.equals(displayedFlagCode)){
    displayedFlagCode=flagKey;
    Bitmap flagImage=CountryFlagArtwork.get(getContext(),flagKey);
    countryFlag.setImageBitmap(flagImage);
    countryFlag.setVisibility(flagImage==null?GONE:VISIBLE);
   }
   suppressDuplicateProvider=(shownProvider.equalsIgnoreCase("OSN")||shownProvider.equalsIgnoreCase("OSNtv")) &&
     s.channelName.toUpperCase(Locale.ROOT).startsWith("OSN");
   countryFlag.setContentDescription(country.isEmpty()?"":tx("بلد القناة: ","Channel market: ")+country);
   updateProviderVisibility();
  nowLine.setText(tx("الآن  ","NOW  ")+(s.title.isEmpty()?tx("بيانات البرنامج غير متاحة","Programme unavailable"):s.title)+range(s.startMs,s.endMs));
  nextLine.setText(tx("التالي  ","NEXT  ")+(s.nextTitle.isEmpty()?tx("غير متاح من EPG","Unavailable from EPG"):s.nextTitle)+range(s.nextStartMs,s.nextEndMs));
  // A user-owned channel image overrides the renderer only while the independent
  // external channel-logo source is enabled. It never changes OEM/CHMAX data.
  Bitmap customChannel=ChannelLogoSourcePrefs.external(getContext())?
    ChannelArtworkOverrides.peek(s.channelId):null;
  if(customChannel!=null)channelLogo=customChannel;
  String style=appearance.signature()+"|"+s.channelId;
  if(!style.equals(displayedLogoStyleAtBitmap)){
   // 194x76dp already includes the requested +18%. Never double-scale.
   float safeScale=Math.min(1f,appearance.size/118f);
   logo.setScaleX(safeScale);logo.setScaleY(safeScale);
   logo.setBackgroundColor("black".equals(appearance.background)?Color.BLACK:Color.TRANSPARENT);
  }
  if(channelLogo==null){
   if(displayedChannelLogo!=null||!style.equals(displayedLogoStyleAtBitmap))
    logo.setImageResource(R.drawable.channel_placeholder);
  }else if(channelLogo!=displayedChannelLogo||!style.equals(displayedLogoStyleAtBitmap)){
   // Original means exact image including its white MBC card. Black processes
   // only an edge-connected outer matte and keeps the coloured logo itself.
   logo.setImageBitmap("black".equals(appearance.background)?
     ChannelLogoBitmap.prepare(channelLogo,
       appearance.removeWhite,appearance.removeBlack,appearance.crop):channelLogo);
  }
  displayedLogoStyleAtBitmap=style;
  displayedChannelLogo=channelLogo;
  logo.setContentDescription("Channel logo · "+s.channelName);
  long length=s.endMs-s.startMs;progress.setProgress(length>0?(int)Math.max(0,Math.min(1000,(System.currentTimeMillis()-s.startMs)*1000/length)):0);progress.setVisibility(length>0?VISIBLE:INVISIBLE);
  ProgrammeClock timer=new ProgrammeClock(s.startMs,s.endMs,System.currentTimeMillis());remaining.setText(timer.known?tx("متبقي  ","REMAINING  ")+ProgrammeClock.format(timer.remainingMs,false):"");remaining.setVisibility(timer.known?VISIBLE:GONE);
  TimeZone deviceZone=TimeZone.getDefault();
  long displayNow=System.currentTimeMillis();
  year.setText(OsdDateFormatter.year(displayNow,deviceZone));
  clock.setText(OsdDateFormatter.dateLine(displayNow,deviceZone,
    prefs.getBoolean("arabic",true)));
  boolean showClock=prefs.getBoolean("osd_clock",true);
  clock.setVisibility(showClock?VISIBLE:GONE);
  year.setVisibility(showClock?VISIBLE:GONE);
  boolean showWeather=!lite&&prefs.getBoolean("osd_weather",false);
  if(showWeather)weather.setText(WeatherRepository.label(getContext()));
  weather.setVisibility(showWeather?VISIBLE:GONE);
  String strength=ReceptionData.value(getContext(),s,"strength"),quality=ReceptionData.value(getContext(),s,"quality");strengthLabel.setText("S: "+(strength.isEmpty()?"—":strength));qualityLabel.setText("Q: "+(quality.isEmpty()?"—":quality));strengthBar.setProgress(percent(strength),true);qualityBar.setProgress(percent(quality),true);strengthBar.setVisibility(strength.isEmpty()?INVISIBLE:VISIBLE);qualityBar.setVisibility(quality.isEmpty()?INVISIBLE:VISIBLE);
  String ca=info.encryption;
  boolean[] caActive=CaBadgePolicy.active(ca);
  for(int i=0;i<caChips.length;i++){
   int value=caActive[i]?1:0;
   if(caDrawn[i]==value)continue;
   caDrawn[i]=value; // No 9 drawable allocations every clock refresh.
   TextView chip=caChips[i];
   chip.setTextColor(value==1?0xff17100a:0xff8f969c);
   GradientDrawable bg=new GradientDrawable();
   bg.setColor(value==1?0xfff8c64a:0xff29313b);
   bg.setCornerRadius(dp(3));
   bg.setStroke(dp(1),value==1?0xfffae08d:0xff404a54);
   chip.setBackground(bg);
   chip.setContentDescription(CaBadgePolicy.LABELS[i]+
       (value==1?" · active":" · inactive"));
  }
  // English "Free-to-air · FTA" must not be shown as encrypted.
  boolean encrypted=caActive[0];
  encryptionLine.setText(ca.isEmpty()?tx("CA —","CA —"):encrypted?"🔒 "+ca:"🔓 "+ca);
  int caState=encrypted?1:0;
  if(encryptionDrawn!=caState){
   encryptionDrawn=caState;
   GradientDrawable caBackground=new GradientDrawable();
   caBackground.setColor(encrypted?0x704c3545:0x66314f42);
   caBackground.setCornerRadius(dp(4));
   caBackground.setStroke(dp(1),encrypted?0xffe3aa7f:0xff74bea1);
   encryptionLine.setBackground(caBackground);
   encryptionLine.setPadding(dp(6),dp(1),dp(6),dp(1));
  }
  encryptionLine.setGravity(Gravity.CENTER);
  encryptionLine.setContentDescription(ca.isEmpty()?tx("معلومات التشفير غير متاحة","CA information unknown"):ca);
  ArrayList<String> details=new ArrayList<>();
  if(!info.broadcast.isEmpty())details.add(info.broadcast);
  if(s.source.startsWith("IPTV")){String audio=prefs.getString("osd_audio","");if(!audio.isEmpty())details.add(audio);}
  if(s.channelId.equals(prefs.getString("subtitle_channel_id",""))&&s.startMs==prefs.getLong("subtitle_event_start_ms",0)&&prefs.getBoolean("overlay_caption",false))details.add("SUB · "+prefs.getString("subtitle_provider","")+String.format(Locale.ROOT," %+.3f s",prefs.getLong("subtitle_offset",0)/1000.0));
  if(!lite){
   SportsMatch related=MatchRepository.get(getContext()).forProgramme(s);
   if(related!=null)details.add(related.league+" · "+related.home+" "+related.score()+" "+related.away+" · "+related.state());
  }
  String receiverMetrics=info.signalDetail()+"   ·   "+
    tx("معدل البت","Bitrate")+" "+(info.bitrate.isEmpty()?"—":info.bitrate);
  String featureText=String.join("   ·   ",details);
  technical.setText((receiverMetrics==null||receiverMetrics.isEmpty()?"":receiverMetrics)+
    (featureText.isEmpty()?"":"   ·   "+featureText));
  technical.setVisibility(VISIBLE);
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
   int state=featureActive[i]?1:0;
   if(featureDrawn[i]==state)continue;
   featureDrawn[i]=state;
   GradientDrawable chip=new GradientDrawable();
   chip.setColor(featureActive[i]?0xffeeeeef:0xff363638);
   chip.setCornerRadius(dp(4));
   featureChips[i].setBackground(chip);
   featureChips[i].setTextColor(featureActive[i]?0xff111114:0xff85858b);
   featureChips[i].setContentDescription(featureNames[i]+(featureActive[i]?" active":" inactive"));
  }
  if(ticker!=null){
   ticker.setVisibility(lite?GONE:VISIBLE);
   if(!lite){
    ArrayList<SportsMatch> selected=new ArrayList<>();
    for(SportsMatch m:MatchRepository.get(getContext()).snapshot()){
     LeagueRepository.League l=LeagueRepository.byId(m.leagueId);
     if(l!=null&&MatchRepository.get(getContext()).selected(l)&&
      m.kickoff>System.currentTimeMillis()-86400000L&&
      m.kickoff<System.currentTimeMillis()+2*86400000L)selected.add(m);
    }
    ticker.bind(selected);
   }
  }
  updatePosterVisibility();
  ReceiverSignalReading sample=DirectReceiverSignal.reading(s);
  boolean noSignal=s.channelId.startsWith("DVB:")&&sample!=null&&sample.locked==0&&
    !prefs.getBoolean("dvb_tuning",false)&&
    s.channelId.equals("DVB:"+prefs.getString("own_dvb_physical",""));
  setStatus(noSignal?tx("لا يوجد إشارة","No signal"):message);
 }

 private void updatePosterVisibility(){
  boolean enabled=prefs.getBoolean("osd_posters",true);
  boolean current=enabled&&currentWorkName.length()>0;
  boolean next=enabled&&nextWorkName.length()>0;
  leftPosterBox.setVisibility(current?VISIBLE:GONE);
  rightPosterBox.setVisibility(next?VISIBLE:GONE);
  currentPoster.setVisibility(current&&currentPoster.getDrawable()!=null?VISIBLE:GONE);
  nextPoster.setVisibility(next&&nextPoster.getDrawable()!=null?VISIBLE:GONE);
 }

 /** Summary is scoped to the same event as each NOW/NEXT poster. */
 void synopses(String now,String next){
  String a=ProgrammeSynopsisText.clean(now,160);
  String b=ProgrammeSynopsisText.clean(next,160);
  // Display a truthful empty-metadata hint instead of an unexplained blank
  // under a successful programme poster.
  if(a.isEmpty()&&currentPoster.getDrawable()!=null)
   a=tx("النبذة غير متاحة","Synopsis unavailable");
  if(b.isEmpty()&&nextPoster.getDrawable()!=null)
   b=tx("النبذة غير متاحة","Synopsis unavailable");
  currentSynopsis.setText(a);nextSynopsis.setText(b);
  currentSynopsis.setVisibility(a.isEmpty()?GONE:VISIBLE);
  nextSynopsis.setVisibility(b.isEmpty()?GONE:VISIBLE);
 }
 void metadata(MediaRepository.Item item){if(item!=null&&!item.id.startsWith("epg:")){String detail=item.release+(item.ratings.isEmpty()?"":" · ★ "+item.ratings);if(!detail.isEmpty()){remaining.setText(remaining.getText()+" · "+detail);remaining.setVisibility(VISIBLE);}if(prefs.getBoolean("osd_description",true)&&!item.summary.isEmpty())setStatus(item.summary);}}
 /** Release displayed images when the ribbon is hidden, keeping no large
  * ImageView references during software video decoding. */
 void clearArtwork(){
  currentPoster.setImageDrawable(null);nextPoster.setImageDrawable(null);
  currentSynopsis.setText("");nextSynopsis.setText("");
  currentWorkName.setText("");nextWorkName.setText("");
  updatePosterVisibility();
  displayedChannelLogo=null;
  logo.setImageDrawable(null);providerLogo.setImageDrawable(null);
  countryFlag.setImageDrawable(null);countryFlag.setVisibility(GONE);
  displayedFlagCode="";
 }
 void provider(Bitmap image){
  if(!ChannelLogoSourcePrefs.providers(getContext())){
   providerLogo.setImageDrawable(null);
   providerName.setVisibility(GONE);
   providerPanel.setVisibility(GONE);
   return;
  }
  SatelliteState live=SatelliteState.read(getContext());
  String displayName=providerName.getText().toString();
  ProviderBrandArt art=ProviderBrandArt.get(getContext());
  Bitmap registered=art.badge(displayName,live.channelName,live.channelId);
  ProviderFamilyRules.Brand family=ProviderFamilyRules.select(
    displayName,live.channelName,live.channelId);
  // Provider-only/manual or independently matched image; never the channel badge.
  if(registered!=null)image=registered;
  providerLogo.setImageBitmap(image==null?null:ProviderBrandArt.outlineDark(image));
  providerLogo.setVisibility(image==null?GONE:VISIBLE);
  providerName.setVisibility(image==null&&!providerName.getText().toString().trim().isEmpty()?VISIBLE:GONE);
  updateProviderVisibility();
 }
 private void updateProviderVisibility(){
  if(!ChannelLogoSourcePrefs.providers(getContext())){
   providerName.setVisibility(GONE);providerPanel.setVisibility(GONE);return;
  }
  boolean hasLogo=providerLogo.getDrawable()!=null;
  boolean hasName=!providerName.getText().toString().trim().isEmpty();
  providerName.setVisibility(!hasLogo&&hasName?VISIBLE:GONE);
  providerPanel.setVisibility(!suppressDuplicateProvider&&(hasLogo||hasName)?VISIBLE:GONE);
 }
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
 private TextView label(String value,int size,boolean bold){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size*textScale);v.setTextColor(ink);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);Typeface original=UserTypeface.get(getContext());if(original==null)original=RxOriginalAssets.font(getContext());
  if(original!=null&&prefs.getBoolean("arabic",true))
   v.setTypeface(original,bold?Typeface.BOLD:Typeface.NORMAL);
  else if(bold)v.setTypeface(null,Typeface.BOLD);
  return v;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
