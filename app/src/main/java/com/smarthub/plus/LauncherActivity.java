package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Simple remote-first home with receiver actions, a live-TV card and real pinned apps. */
public class LauncherActivity extends HubActivity {
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newFixedThreadPool(2);
 private final android.util.LruCache<String,Bitmap> icons=new android.util.LruCache<String,Bitmap>(2*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
 private TextView clock,date,connection,channel,programme,weather;
 private Button usbAdbButton;
 private RxMotionWallpaper motionWallpaper;
 private boolean usbAdbStartupShown;
 private ImageView channelLogo;private LinearLayout appsGrid;
 private View firstTile;private boolean resumed,loading,compact,light;
 private String savedFocus;
 private long nextReceiverRefresh;private volatile boolean destroyed;private int render,accent;private String logoKey="";
 private List<AppCatalogue.App> catalogue=Collections.emptyList();
 private final Runnable tick=new Runnable(){public void run(){update();if(resumed)main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle s){PlaybackResourceMode.clearStaleOnLauncherStart(this);IraqHubSyncScheduler.ensure(this);if(!prefs().getBoolean("launcher_reference_v34",false))prefs().edit().putBoolean("launcher_reference_v34",true).putInt("launcher_theme",0).putString("osd_accent","#E84C58").putLong("osd_changed_at",System.currentTimeMillis()).apply();super.onCreate(s);savedFocus=s==null?null:s.getString("rx_focus");build();io.execute(()->{List<AppCatalogue.App> found=AppCatalogue.load(this);main.post(()->{if(!destroyed){catalogue=found;paintApps();}});});}
 private void build(){
  render++;logoKey="";
  compact=getResources().getDisplayMetrics().heightPixels/
    getResources().getDisplayMetrics().density<620;
  accent=RxSkin.accent(this);
  light=RxSkin.selected(this)==1;
  boolean classic="classic".equals(prefs().getString(RxSkin.LAYOUT,"horizon"));
  firstTile=null;weather=null;
  final int stamp=render;
  FrameLayout stage=new FrameLayout(this);
  stage.setBackground(RxSkin.wallpaper(this));
  ImageView backdrop=new ImageView(this);
  backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
  stage.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
  int theme=RxSkin.selected(this);
  motionWallpaper=null;
  if(theme==9||theme==12){
   String file=theme==9?"black-hole.mp4":"waterfall.mp4";
   motionWallpaper=new RxMotionWallpaper(this,"rx239/walls/"+file);
   stage.addView(motionWallpaper,new FrameLayout.LayoutParams(-1,-1));
  }
  io.execute(()->{
   Drawable art=RxOriginalAssets.wallpaperArt(this,theme,1440);
   main.post(()->{if(!destroyed&&stamp==render&&art!=null)backdrop.setImageDrawable(art);});
  });
  View shade=new View(this);
  shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
    light?new int[]{0x90ffffff,0x30ffffff,0xccf4f4f9}:
     new int[]{0x61000000,0x200b1119,0xc90b1018}));
  stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout root=col();
  root.setLayoutDirection(ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
  root.setPadding(dp(compact?26:44),dp(compact?18:29),
    dp(compact?26:44),dp(compact?14:26));
  stage.addView(root,new FrameLayout.LayoutParams(-1,-1));
  setContentView(stage);
  // RX top edge: separate branding and clock; settings remain one button away.
  LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
  TextView brand=text("IRAQhub",compact?21:27,true);
  brand.setTextColor(homeInk());brand.setLetterSpacing(.09f);
  header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  connection=text("",12,false);connection.setPadding(dp(9),0,dp(9),0);
  header.addView(connection);
  usbAdbButton=button("USB ADB");
  usbAdbButton.setTextSize(11);
  usbAdbButton.setContentDescription(tx("تصحيح USB · إعدادات المطور",
    "USB ADB debugging · developer options"));
  usbAdbButton.setOnClickListener(v->startActivity(new android.content.Intent(this,ReceiverHardwareBridgeActivity.class)));
  header.addView(usbAdbButton,new LinearLayout.LayoutParams(dp(compact?86:105),dp(39)));
  Button appearance=button(tx("المظهر","Appearance"));
  appearance.setTextSize(12);appearance.setOnClickListener(v->themes());
  header.addView(appearance,new LinearLayout.LayoutParams(dp(compact?92:110),dp(39)));
  LinearLayout time=col();time.setGravity(Gravity.CENTER);
  time.setPadding(dp(12),0,dp(12),0);
  clock=text("",compact?23:28,true);clock.setGravity(Gravity.CENTER);
  clock.setTextColor(homeInk());clock.setTextDirection(View.TEXT_DIRECTION_LTR);
  date=text("",compact?10:12,false);date.setGravity(Gravity.CENTER);date.setTextColor(homeMuted());
  time.addView(clock);time.addView(date);header.addView(time);
  root.addView(header,new LinearLayout.LayoutParams(-1,dp(compact?46:56)));

  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
  scroll.setVerticalScrollBarEnabled(false);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout body=col();body.setPadding(dp(2),dp(3),dp(2),dp(22));
  scroll.addView(body);

  if(classic)receiverRow(body);
  if(!classic){
  TextView welcome=text(tx("مساحتك الترفيهية","Your entertainment space"),compact?24:34,true);
  welcome.setTextColor(homeInk());body.addView(welcome);
  TextView subtitle=text(tx("التلفزيون والتطبيقات والمباريات في مكان واحد",
    "Live television, apps and sports in one place"),compact?12:16,false);
  subtitle.setTextColor(homeMuted());
  LinearLayout.LayoutParams subParams=new LinearLayout.LayoutParams(-1,-2);
  subParams.topMargin=dp(2);subParams.bottomMargin=dp(compact?14:22);
  body.addView(subtitle,subParams);
  }

  LinearLayout hero=row();hero.setClipChildren(false);
  body.addView(hero,new LinearLayout.LayoutParams(-1,dp(classic?(compact?142:176):(compact?164:216))));
  FrameLayout live=tile("live-tv",0xff161c24,this::watch);
  if(firstTile==null)firstTile=live;live.setContentDescription(tx("التلفزيون المباشر","Live satellite TV"));
  ImageView rxArt=new ImageView(this);rxArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
  String liveArt=classic?"live_neutral":light?"live_light":"live_dark";
  rxArt.setImageResource(light?R.drawable.rx_live_horizontal_light:R.drawable.rx_live_horizontal_dark);
  io.execute(()->{
   Drawable art=RxOriginalAssets.drawable(this,liveArt,950);
   main.post(()->{if(!destroyed&&stamp==render&&art!=null)rxArt.setImageDrawable(art);});
  });
  live.addView(rxArt,new FrameLayout.LayoutParams(-1,-1));
  View artShade=new View(this);artShade.setBackground(new GradientDrawable(
    GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xef000000,0x8a000000,0x09000000}));
  live.addView(artShade,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout liveInfo=col();liveInfo.setGravity(Gravity.BOTTOM);
  liveInfo.setPadding(dp(18),dp(14),dp(17),dp(17));
  FrameLayout.LayoutParams infoPosition=new FrameLayout.LayoutParams(-1,-1,Gravity.BOTTOM);
  live.addView(liveInfo,infoPosition);
  TextView liveTitle=text(tx("التلفزيون المباشر","Live TV"),compact?24:31,true);
  liveTitle.setTextColor(0xffffffff);liveInfo.addView(liveTitle);
  LinearLayout currentChannel=row();
  channelLogo=new ImageView(this);channelLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  currentChannel.addView(channelLogo,new LinearLayout.LayoutParams(dp(40),dp(28)));
  channel=text("",compact?12:15,true);
  channel.setTextColor(0xfff1f1f5);channel.setSingleLine(true);
  channel.setEllipsize(android.text.TextUtils.TruncateAt.END);
  currentChannel.addView(channel,new LinearLayout.LayoutParams(0,-2,1));
  liveInfo.addView(currentChannel);
  programme=text("",compact?11:13,false);
  programme.setTextColor(0xffd0d7e4);programme.setSingleLine(true);
  programme.setEllipsize(android.text.TextUtils.TruncateAt.END);
  liveInfo.addView(programme);
  LinearLayout.LayoutParams liveSize=new LinearLayout.LayoutParams(0,-1,1.65f);
  liveSize.setMargins(dp(3),dp(3),dp(8),dp(5));hero.addView(live,liveSize);
  rxFeature(hero,"apps-big",tx("التطبيقات","Applications"),
    tx("المفضلة وجميع التطبيقات","Your apps and favourites"),
    HomeIcon.APPS,()->open(AppsActivity.class),1f);
  rxFeature(hero,"settings-big",tx("الإعدادات","Settings"),
    tx("القنوات والمظهر","Channels & appearance"),
    HomeIcon.SETTINGS,this::settings,1f);

  LinearLayout smallCards=row();smallCards.setClipChildren(false);
  LinearLayout.LayoutParams smallPos=new LinearLayout.LayoutParams(-1,dp(compact?80:105));
  smallPos.topMargin=dp(compact?10:16);body.addView(smallCards,smallPos);
  weather=rxFeature(smallCards,"weather-card",tx("الطقس","Weather"),
    weatherSummary(),HomeIcon.WIFI,this::showWeather,1f);
  rxFeature(smallCards,"prayer-card",tx("الصلاة","Prayer"),
    tx("المواقيت والتنبيهات","Prayer times and reminders"),HomeIcon.GUIDE,
    ()->open(PrayerActivity.class),1f);
  rxFeature(smallCards,"sport-card",tx("المباريات","Matches"),
    tx("النتائج والمواعيد","Scores and schedule"),HomeIcon.SPORT,
    ()->open(SportsCenterActivity.class),1f);
  if(!classic)receiverRow(body);

  section(body,tx("تطبيقاتي","MY APPLICATIONS"));
  LinearLayout appTop=row();
  appTop.addView(sectionText(tx("تطبيقاتك المختارة","Your pinned apps")),
    new LinearLayout.LayoutParams(0,-2,1));
  Button customize=button(tx("＋ تخصيص","＋ Customize"));
  customize.setTextSize(12);customize.setOnClickListener(v->chooseApps());
  appTop.addView(customize,new LinearLayout.LayoutParams(dp(128),dp(38)));
  body.addView(appTop);
  appsGrid=col();
  body.addView(appsGrid,new LinearLayout.LayoutParams(-1,dp(compact?132:180)));
  paintApps();

  section(body,tx("المفضلة والمشاهدة","YOUR SHORTCUTS"));
  LinearLayout favourites=row();
  body.addView(favourites,new LinearLayout.LayoutParams(-1,dp(compact?46:55)));
  dock(favourites,tx("المفضلة","Favourites"),HomeIcon.CHANNELS,
    ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)
      .putExtra("favorites",true)));
  dock(favourites,tx("الراديو","Radio"),HomeIcon.PLAY,
    ()->startActivity(new Intent(this,ReceiverChannelsActivity.class)
      .putExtra("type",1)));
  dock(favourites,tx("الأقمار","Satellites"),HomeIcon.SATELLITE,
    ()->open(SatelliteBrowserActivity.class));
  dock(favourites,tx("البحث","Search"),HomeIcon.GUIDE,
    ()->open(HubSearchActivity.class));
  dock(favourites,tx("المظهر","Appearance"),HomeIcon.THEME,this::themes);

  TextView hint=text(tx("الأسهم للتنقل  •  OK للفتح  •  زر الرجوع يحفظ الرئيسية",
    "Arrows navigate  •  OK opens  •  Back keeps Home"),11,false);
  hint.setTextColor(homeMuted());hint.setGravity(Gravity.CENTER);
  hint.setPadding(0,dp(9),0,0);body.addView(hint);
  root.post(()->{
   if(destroyed)return;
   if(restoreFocus(savedFocus))savedFocus=null;
   else if(firstTile!=null)firstTile.requestFocus();
  });
  update();
 }
 /** RX card geometry and remote focus, using existing IRAQhub actions. */
 private TextView rxFeature(LinearLayout holder,String id,String title,String subtitle,
                        int glyph,Runnable action,float weight){
  boolean small=id.endsWith("-card");
  FrameLayout cell=tile(id,0xff23242b,action);
  cell.setContentDescription(title);
  if("prayer-card".equals(id)){
   ImageView art=new ImageView(this);
   art.setScaleType(ImageView.ScaleType.CENTER_CROP);
   art.setAlpha(light?.12f:.28f);
   cell.addView(art,new FrameLayout.LayoutParams(-1,-1));
   int stamp=render;
   io.execute(()->{
    Drawable image=RxOriginalAssets.drawable(this,"prayer",480);
    main.post(()->{if(!destroyed&&stamp==render&&image!=null)art.setImageDrawable(image);});
   });
  }
  LinearLayout inside=col();inside.setGravity(Gravity.BOTTOM);
  inside.setPadding(dp(16),dp(small?6:14),dp(16),dp(small?8:16));
  FrameLayout.LayoutParams fill=new FrameLayout.LayoutParams(-1,-1);
  cell.addView(inside,fill);
  ImageView icon=icon(glyph,homeInk());
  int iconSize=dp(small?(compact?22:30):(compact?28:41));
  inside.addView(icon,new LinearLayout.LayoutParams(iconSize,iconSize));
  TextView label=text(title,small?(compact?15:18):(compact?17:24),true);
  label.setSingleLine(true);label.setIncludeFontPadding(false);
  label.setTextColor(homeInk());inside.addView(label);
  TextView detail=text(subtitle,small?(compact?10:11):(compact?10:13),false);
  detail.setIncludeFontPadding(false);
  detail.setTextColor(homeMuted());
  detail.setSingleLine(true);
  detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
  inside.addView(detail);
  LinearLayout.LayoutParams cellSize=new LinearLayout.LayoutParams(0,-1,weight);
  cellSize.setMargins(dp(5),dp(3),dp(5),dp(5));holder.addView(cell,cellSize);
  return detail;
 }
 private void section(LinearLayout parent,String title){LinearLayout wrap=row();wrap.setGravity(Gravity.CENTER_VERTICAL);View line=new View(this);line.setBackgroundColor(RxSkin.accent(this));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(3),dp(17));lp.setMargins(0,0,dp(9),0);wrap.addView(line,lp);wrap.addView(sectionText(title),new LinearLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(compact?25:32));p.topMargin=dp(5);parent.addView(wrap,p);}
 private TextView sectionText(String title){TextView v=text(title,16,true);v.setGravity((ar?Gravity.RIGHT:Gravity.LEFT)|Gravity.CENTER_VERTICAL);v.setTextColor(homeInk());return v;}
 private void receiverRow(LinearLayout body){
  section(body,tx("القنوات والرسيفر","SATELLITE TV"));
  LinearLayout nav=row();
  nav.setClipChildren(false);
  body.addView(nav);
  nav(nav,"channels",tx("القنوات","Channels"),HomeIcon.CHANNELS,()->open(ReceiverChannelsActivity.class));
  nav(nav,"receiver",tx("التركيب","Installation"),HomeIcon.SATELLITE,()->open(ReceiverToolsActivity.class));
  nav(nav,"guide",tx("دليل البرامج","EPG"),HomeIcon.GUIDE,()->open(SatelliteGuideActivity.class));
  nav(nav,"signal",tx("الإشارة","Signal"),HomeIcon.WIFI,()->open(SignalInformationActivity.class));
  nav(nav,"other",tx("الأدوات","Tools"),HomeIcon.APPS,()->open(ToolsActivity.class));
 }
 private int homeInk(){return light?0xff202024:0xfff1f1f5;}
 private int homeMuted(){return light?0xff494952:0xffc5c5cc;}
 private FrameLayout tile(String id,int color,Runnable action){
  FrameLayout v=new FrameLayout(this);
  v.setTag(id);v.setFocusable(true);v.setClickable(true);
  v.setDefaultFocusHighlightEnabled(false);
  boolean glass=prefs().getBoolean("rx_glass_effect",true);
  GradientDrawable panel=shape(light?(glass?0xe8ffffff:0xfff6f6fa):
    ((color&0xffffff)|(glass?0xda000000:0xff000000)),16);
  panel.setStroke(dp(1),light?0x66474750:0x66e2e2e8);
  v.setBackground(panel);v.setClipToOutline(true);v.setElevation(dp(3));
  GradientDrawable outline=shape(Color.TRANSPARENT,16);
  outline.setStroke(dp(3),light?0xff237546:0xfff2c96b);
  StateListDrawable focus=new StateListDrawable();
  focus.addState(new int[]{android.R.attr.state_focused},outline);
  focus.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));
  v.setForeground(focus);
  v.setOnFocusChangeListener((view,focused)->{
   float scale=focused?1.035f:1f;
   view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
  });
  v.setOnClickListener(w->action.run());
  return v;
 }
 private ImageView icon(int kind,int color){
  ImageView image=new ImageView(this);
  image.setImageDrawable(new HomeIcon(kind,color));
  image.setScaleType(ImageView.ScaleType.FIT_CENTER);
  image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  return image;
 }
 private void nav(LinearLayout row,String id,String title,int symbol,Runnable action){
  FrameLayout tile=tile(id,0xff252840,action);
  tile.setContentDescription(title);
  if(firstTile==null)firstTile=tile;
  LinearLayout content=col();content.setGravity(Gravity.CENTER);
  content.setPadding(dp(6),dp(4),dp(6),dp(4));
  tile.addView(content,new FrameLayout.LayoutParams(-1,-1));
  int iconSize=dp(compact?24:32);
  content.addView(icon(symbol,homeInk()),new LinearLayout.LayoutParams(iconSize,iconSize));
  TextView label=text(title,compact?14:17,true);
  label.setGravity(Gravity.CENTER);label.setTextColor(homeInk());
  label.setPadding(0,dp(4),0,0);label.setMaxLines(2);
  content.addView(label,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(0,dp(compact?66:84),1);
  position.setMargins(dp(4),dp(3),dp(4),dp(3));row.addView(tile,position);
 }
 private void dock(LinearLayout row,String title,int symbol,Runnable action){
  FrameLayout tile=tile("dock-"+title,0xff212637,action);
  tile.setContentDescription(title);
  TextView label=text(title,compact?12:14,true);
  label.setGravity(Gravity.CENTER);label.setTextColor(homeInk());
  tile.addView(label,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(0,-1,1);
  position.setMargins(dp(4),dp(3),dp(4),dp(3));row.addView(tile,position);
 }
 private List<AppCatalogue.App> pinned(){List<AppCatalogue.App> out=new ArrayList<>();Map<String,AppCatalogue.App> available=new LinkedHashMap<>();for(AppCatalogue.App a:catalogue)available.put(a.pkg,a);String saved=prefs().getString("launcher_pins","");if(!saved.isEmpty())try{JSONArray a=new JSONArray(saved);for(int i=0;i<Math.min(9,a.length());i++){AppCatalogue.App app=available.remove(a.optString(i));if(app!=null)out.add(app);}return out;}catch(Exception ignored){}for(String pkg:new String[]{"com.google.android.youtube.tv","com.netflix.ninja","com.tosmart.filemanagerpro","com.android.vending","com.droidlogic.tv.settings"}){AppCatalogue.App a=available.remove(pkg);if(a!=null)out.add(a);}for(AppCatalogue.App a:available.values())if(out.size()<9)out.add(a);return out;}
 private void paintApps(){if(appsGrid==null)return;View focused=getCurrentFocus();String previous=focused!=null&&focused.getTag() instanceof String?(String)focused.getTag():null;appsGrid.removeAllViews();List<AppCatalogue.App> pins=pinned();int stamp=render;for(int y=0;y<2;y++){LinearLayout line=row();appsGrid.addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int x=0;x<5;x++){int index=y*5+x;AppCatalogue.App app=index<pins.size()?pins.get(index):null;boolean all=index==9;FrameLayout t=tile(all?"apps":app==null?"add-app-"+index:"app-"+app.pkg,all?0xff4d3d95:0xff29294b,()->{if(all)open(AppsActivity.class);else if(app==null)chooseApps();else try{startActivity(AppCatalogue.launch(app));}catch(Exception e){toast(tx("تعذر فتح التطبيق","Cannot open app"));}});t.setContentDescription(all?tx("كل التطبيقات","All apps"):app==null?tx("إضافة تطبيق","Add app"):app.label);LinearLayout content=col();content.setGravity(Gravity.CENTER);content.setDuplicateParentStateEnabled(true);content.setPadding(dp(5),dp(5),dp(5),dp(5));t.addView(content,new FrameLayout.LayoutParams(-1,-1));ImageView image=icon(all?HomeIcon.APPS:HomeIcon.PLUS,all?0xffd9cdff:accent);content.addView(image,new LinearLayout.LayoutParams(dp(compact?24:38),dp(compact?30:38)));TextView label=text(all?tx("كل التطبيقات","All apps"):app==null?tx("إضافة تطبيق","Add app"):app.label,compact?12:14,true);label.setGravity(Gravity.CENTER);label.setTextColor(homeInk());label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(0,dp(5),0,0);content.addView(label,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(t,p);if(app!=null&&!all){Bitmap b=icons.get(app.pkg);if(b!=null)image.setImageBitmap(b);else io.execute(()->{Bitmap bitmap=null;try{Drawable d=app.info.loadIcon(getPackageManager());bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);d.setBounds(0,0,96,96);d.draw(new Canvas(bitmap));icons.put(app.pkg,bitmap);}catch(Exception ignored){}Bitmap result=bitmap;main.post(()->{if(!destroyed&&stamp==render&&result!=null)image.setImageBitmap(result);});});}}}if(!restoreFocus(previous)&&savedFocus!=null&&savedFocus.startsWith("app-")&&restoreFocus(savedFocus))savedFocus=null;}
 private void chooseApps(){if(catalogue.isEmpty()){toast(tx("انتظر تحميل التطبيقات","Wait for the app list"));return;}Set<String> chosen=new LinkedHashSet<>();for(AppCatalogue.App a:pinned())chosen.add(a.pkg);String[] labels=new String[catalogue.size()];boolean[] checks=new boolean[labels.length];for(int i=0;i<labels.length;i++){labels[i]=catalogue.get(i).label;checks[i]=chosen.contains(catalogue.get(i).pkg);}new AlertDialog.Builder(this).setTitle(tx("اختر حتى 9 تطبيقات مفضلة","Choose up to 9 favourites")).setMultiChoiceItems(labels,checks,(dialog,index,on)->{String pkg=catalogue.get(index).pkg;if(on&&chosen.size()>=9){((AlertDialog)dialog).getListView().setItemChecked(index,false);toast(tx("يمكن تثبيت تسعة تطبيقات","You can pin nine apps"));}else if(on)chosen.add(pkg);else chosen.remove(pkg);}).setPositiveButton(tx("حفظ","Save"),(d,w)->{prefs().edit().putString("launcher_pins",new JSONArray(chosen).toString()).apply();paintApps();}).setNegativeButton(tx("إلغاء","Cancel"),null).show();}
 private void settings(){open(HubSettingsActivity.class);}
 private void themes(){open(RxAppearanceActivity.class);}
 private void watch(){open(DvbPlaybackActivity.class);}
 private void open(Class<?> type){
  if(motionWallpaper!=null)motionWallpaper.setPlaying(false);
  startActivity(new Intent(this,type));
 }
 private void syncMotionWallpaper(){
  if(motionWallpaper!=null)motionWallpaper.setPlaying(resumed&&hasWindowFocus()&&
    prefs().getBoolean("rx_wall_motion",false)&&!PlaybackResourceMode.active());
 }
 @Override public void onWindowFocusChanged(boolean focused){
  super.onWindowFocusChanged(focused);
  syncMotionWallpaper();
 }
 private boolean restoreFocus(String tag){
  if(tag==null)return false;
  View target=getWindow().getDecorView().findViewWithTag(tag);
  return target!=null&&target.requestFocus();
 }
 @Override protected void onSaveInstanceState(Bundle out){
  super.onSaveInstanceState(out);
  View focused=getCurrentFocus();
  if(focused!=null&&focused.getTag() instanceof String)out.putString("rx_focus",(String)focused.getTag());
 }
 private String weatherSummary(){
  String label=WeatherRepository.label(this);
  return label.isEmpty()?tx("اضغط لإعداد الطقس","Press to set up weather"):label.split("\n",2)[0];
 }
 private void showWeather(){
  String label=WeatherRepository.label(this);
  AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(tx("الطقس","Weather"))
   .setPositiveButton(tx("إغلاق","Close"),null);
  if(label.isEmpty()){
   dialog.setMessage(tx("فعّل الطقس واضبط المدينة في إعدادات شريط المعلومات.","Enable weather and choose a city in InfoBar settings."))
    .setNeutralButton(tx("إعداد الطقس","Weather settings"),(d,w)->open(OsdSettingsActivity.class));
  }else{
   WeatherRepository.update(this);
   dialog.setMessage(label)
    .setNeutralButton(tx("الإعدادات","Settings"),(d,w)->open(OsdSettingsActivity.class));
  }
  dialog.show();
 }
 private void update(){
  if(clock==null||destroyed)return;
  syncMotionWallpaper();
  Date now=new Date();
  clock.setText(new SimpleDateFormat("HH:mm",Locale.ROOT).format(now));
  date.setText(new SimpleDateFormat("EEE، d MMM",ar?Locale.forLanguageTag("ar-IQ"):Locale.ENGLISH).format(now));
  if(weather!=null)weather.setText(weatherSummary());
  ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  NetworkCapabilities nc=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
  boolean online=nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  connection.setText(online?tx("● متصل","● Online"):tx("● غير متصل","● Offline"));
  connection.setTextColor(online?(light?0xff237546:0xff73dfbf):(light?0xff765031:0xffd2ad8f));
  long elapsed=SystemClock.elapsedRealtime();
  if(loading||elapsed<nextReceiverRefresh)return;
  nextReceiverRefresh=elapsed+4000;
  loading=true;
  int stamp=render;
  io.execute(()->{
   SatelliteState state=ReceiverInfoRepository.get(this).current();
   main.post(()->{
    loading=false;
    if(destroyed||stamp!=render)return;
    channel.setText(state.channelName.isEmpty()?tx("قنواتك الفضائية بلمسة واحدة","Your satellite channels in one click"):
     tx("آخر قناة · ","Last channel · ")+state.channelName);
    programme.setText(state.title);
    String key=state.channelId+"|"+state.channelName+"|"+prefs().getLong("logo_mapping_changed_at",0)+"|"+
     prefs().getLong("native_logos_changed_at",0);
    if(logoKey.equals(key))return;
    logoKey=key;
    channelLogo.setImageResource(R.drawable.channel_placeholder);
    if(state.channelName.isEmpty())return;
    LogoRepository.get(this).request(state.channelName,state.channelId,new LogoRepository.Listener(){
     public void channel(Bitmap bitmap){
      if(!destroyed&&stamp==render&&logoKey.equals(key)&&bitmap!=null)channelLogo.setImageBitmap(bitmap);
     }
     public void provider(String name,Bitmap bitmap){}
    });
   });
  });
 }
 @Override protected void onResume(){
  super.onResume();resumed=true;nextReceiverRefresh=0;main.post(tick);
  WeatherRepository.update(this);
  AdbIpConnection.checkOnLauncherStart(this); // opt-in; no UI/network blocking
  if(usbAdbButton!=null){
   boolean enabled=UsbAdbSetup.enabled(this);
   long verifiedAt=prefs().getLong("adb_ip_checked_at",0);
   boolean verified=prefs().getBoolean("adb_ip_last_authorized",false)&&
     verifiedAt>0&&System.currentTimeMillis()-verifiedAt<300000;
   usbAdbButton.setText(verified?"ADB IP ✓":enabled?"ADB ON":"USB ADB");
   usbAdbButton.setContentDescription(UsbAdbSetup.status(this)+
     " · "+prefs().getString("adb_ip_status","ADB_IP_UNTESTED"));
  }
  boolean shouldOffer=UsbAdbSetup.shouldPrompt(UsbAdbSetup.enabled(this),
    UsbAdbSetup.promptEnabled(this),usbAdbStartupShown);
  if(shouldOffer){
   usbAdbStartupShown=true;
   main.postDelayed(()->{
    if(resumed&&!destroyed&&!isFinishing()&&!isDestroyed()&&
      !UsbAdbSetup.enabled(this))UsbAdbSetup.show(this);
   },400);
  }
 }@Override protected void onPause(){resumed=false;syncMotionWallpaper();main.removeCallbacks(tick);super.onPause();}
 @Override public void onBackPressed(){/* HOME remains IRAQhub. */}
 @Override protected void onDestroy(){destroyed=true;resumed=false;main.removeCallbacksAndMessages(null);io.shutdownNow();icons.evictAll();super.onDestroy();}
}
