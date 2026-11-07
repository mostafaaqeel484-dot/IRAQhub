package com.smarthub.plus;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
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
 private TextView clock,connection,channel,programme;private ImageView channelLogo;private LinearLayout appsGrid;
 private View firstTile;private boolean resumed,loading,compact;private volatile boolean destroyed;private int render,accent;private String logoKey="";
 private List<AppCatalogue.App> catalogue=Collections.emptyList();
 private final Runnable tick=new Runnable(){public void run(){update();if(resumed)main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle s){PlaybackResourceMode.clearStaleOnLauncherStart(this);IraqHubSyncScheduler.ensure(this);if(!prefs().getBoolean("launcher_reference_v34",false))prefs().edit().putBoolean("launcher_reference_v34",true).putInt("launcher_theme",0).putString("osd_accent","#E84C58").putLong("osd_changed_at",System.currentTimeMillis()).apply();super.onCreate(s);build();io.execute(()->{List<AppCatalogue.App> found=AppCatalogue.load(this);main.post(()->{if(!destroyed){catalogue=found;paintApps();}});});}
 private void build(){
  render++;logoKey="";
  compact=getResources().getDisplayMetrics().heightPixels/
    getResources().getDisplayMetrics().density<620;
  accent=RxSkin.accent(this);
  // The actual RX horizon hierarchy, not the old 5-button IRAQhub dashboard.
  FrameLayout stage=new FrameLayout(this);
  stage.setBackground(RxOriginalAssets.wallpaper(this));
  View shade=new View(this);
  shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
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
  brand.setTextColor(0xfff1f1f5);brand.setLetterSpacing(.09f);
  header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  connection=text("",12,false);connection.setPadding(dp(9),0,dp(9),0);
  header.addView(connection);
  Button appearance=button(tx("المظهر","Appearance"));
  appearance.setTextSize(12);appearance.setOnClickListener(v->themes());
  header.addView(appearance,new LinearLayout.LayoutParams(dp(110),dp(39)));
  clock=text("",compact?25:32,true);clock.setGravity(Gravity.CENTER);
  clock.setTextDirection(View.TEXT_DIRECTION_LTR);
  clock.setPadding(dp(18),0,0,0);header.addView(clock);
  root.addView(header,new LinearLayout.LayoutParams(-1,dp(compact?46:56)));

  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
  scroll.setVerticalScrollBarEnabled(false);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout body=col();body.setPadding(dp(2),dp(3),dp(2),dp(22));
  scroll.addView(body);

  TextView welcome=text(tx("مساحتك الترفيهية","Your entertainment space"),compact?26:38,true);
  welcome.setTextColor(0xfff1f1f5);body.addView(welcome);
  TextView subtitle=text(tx("التلفزيون والتطبيقات والمباريات في مكان واحد",
    "Live television, apps and sports in one place"),compact?12:16,false);
  subtitle.setTextColor(0xffbbc0d0);
  LinearLayout.LayoutParams subParams=new LinearLayout.LayoutParams(-1,-2);
  subParams.topMargin=dp(2);subParams.bottomMargin=dp(compact?14:22);
  body.addView(subtitle,subParams);

  LinearLayout hero=row();hero.setClipChildren(false);
  body.addView(hero,new LinearLayout.LayoutParams(-1,dp(compact?185:255)));
  FrameLayout live=tile("live-tv",0xff161c24,this::watch);
  firstTile=live;live.setContentDescription(tx("التلفزيون المباشر","Live satellite TV"));
  ImageView rxArt=new ImageView(this);rxArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
  RxOriginalAssets.card(this,rxArt,RxSkin.selected(this)==1?"live_light":"live_dark");
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
  LinearLayout.LayoutParams smallPos=new LinearLayout.LayoutParams(-1,dp(compact?95:132));
  smallPos.topMargin=dp(compact?10:16);body.addView(smallCards,smallPos);
  rxFeature(smallCards,"weather-card",tx("الطقس","Weather"),
    tx("الطقس والمعلومات","Forecast and information"),HomeIcon.WIFI,
    ()->open(ToolsActivity.class),1f);
  rxFeature(smallCards,"prayer-card",tx("الصلاة","Prayer"),
    tx("المواقيت القادمة","Next prayers"),HomeIcon.GUIDE,
    ()->open(PrayerActivity.class),1f);
  rxFeature(smallCards,"sport-card",tx("المباريات","Matches"),
    tx("النتائج والمواعيد","Scores and schedule"),HomeIcon.CHANNELS,
    ()->open(SportsCenterActivity.class),1f);

  // Keep the existing real favourites, apps, channel-management and quick
  // shortcuts below the RX hero rather than deleting any app functionality.
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

  section(body,tx("القنوات والرسيفر","RECEIVER"));
  LinearLayout nav=row();body.addView(nav);
  nav(nav,"channels",tx("القنوات","Channels"),HomeIcon.CHANNELS,0xff172536,
    ()->open(ReceiverChannelsActivity.class));
  nav(nav,"receiver",tx("التركيب","Installation"),HomeIcon.SATELLITE,0xff172536,
    ()->open(ReceiverToolsActivity.class));
  nav(nav,"guide",tx("دليل البرامج","EPG"),HomeIcon.GUIDE,0xff172536,
    ()->open(SatelliteGuideActivity.class));
  nav(nav,"signal",tx("الإشارة","Signal"),HomeIcon.WIFI,0xff172536,
    ()->open(SignalInformationActivity.class));
  nav(nav,"other",tx("إضافات","Other"),HomeIcon.APPS,0xff172536,
    ()->open(ToolsActivity.class));

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
  hint.setTextColor(0xffbcc3cf);hint.setGravity(Gravity.CENTER);
  hint.setPadding(0,dp(9),0,0);body.addView(hint);
  root.post(()->{if(!destroyed&&firstTile!=null)firstTile.requestFocus();});
  update();
 }
 /** RX card geometry and remote focus, using existing IRAQhub actions. */
 private void rxFeature(LinearLayout holder,String id,String title,String subtitle,
                        int glyph,Runnable action,float weight){
  FrameLayout cell=tile(id,0xff23242b,action);
  cell.setContentDescription(title+" · "+subtitle);
  LinearLayout inside=col();inside.setGravity(Gravity.BOTTOM);
  inside.setPadding(dp(16),dp(14),dp(16),dp(16));
  FrameLayout.LayoutParams fill=new FrameLayout.LayoutParams(-1,-1);
  cell.addView(inside,fill);
  ImageView icon=icon(glyph,0xffe2e2e8);
  inside.addView(icon,new LinearLayout.LayoutParams(dp(compact?28:41),dp(compact?28:41)));
  TextView label=text(title,compact?17:24,true);
  label.setTextColor(0xfff1f1f5);inside.addView(label);
  TextView detail=text(subtitle,compact?10:13,false);
  detail.setTextColor(0xffc5c5cc);
  detail.setSingleLine(true);
  detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
  inside.addView(detail);
  LinearLayout.LayoutParams cellSize=new LinearLayout.LayoutParams(0,-1,weight);
  cellSize.setMargins(dp(5),dp(3),dp(5),dp(5));holder.addView(cell,cellSize);
 }
 private void section(LinearLayout parent,String title){LinearLayout wrap=row();wrap.setGravity(Gravity.CENTER_VERTICAL);View line=new View(this);line.setBackgroundColor(RxSkin.accent(this));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(3),dp(17));lp.setMargins(0,0,dp(9),0);wrap.addView(line,lp);wrap.addView(sectionText(title),new LinearLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(compact?25:32));p.topMargin=dp(5);parent.addView(wrap,p);}
 private TextView sectionText(String title){TextView v=text(title,16,true);v.setGravity((ar?Gravity.RIGHT:Gravity.LEFT)|Gravity.CENTER_VERTICAL);v.setTextColor(0xffe2e2e8);return v;}
 private FrameLayout tile(String id,int color,Runnable action){
  FrameLayout v=new FrameLayout(this);v.setTag(id);v.setContentDescription(null);
  v.setFocusable(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);
  v.setBackground(RxSkin.focus(this));v.setClipToOutline(true);
  v.setElevation(dp(3));
  v.setOnFocusChangeListener((view,focused)->{
   float scale=focused?1.035f:1f;
   view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
  });
  v.setOnClickListener(w->action.run());
  return v;
 }
 private GradientDrawable gradient(int c){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(Math.min(255,Color.red(c)+14),Math.min(255,Color.green(c)+13),Math.min(255,Color.blue(c)+22)),c});g.setCornerRadius(dp(14));return g;}
 private void focusLabel(TextView v){v.setDuplicateParentStateEnabled(true);v.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xff141625,WHITE}));}
 private ImageView icon(int kind,int color){ImageView image=new ImageView(this);image.setDuplicateParentStateEnabled(true);image.setImageDrawable(new HomeIcon(kind,color));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;}
 private void nav(LinearLayout row,String id,String title,int symbol,int color,Runnable action){FrameLayout t=tile(id,color,action);t.setContentDescription(title);if(id.equals("channels"))firstTile=t;LinearLayout content=col();content.setDuplicateParentStateEnabled(true);content.setGravity(Gravity.CENTER);content.setPadding(dp(9),dp(9),dp(9),dp(9));t.addView(content,new FrameLayout.LayoutParams(-1,-1));content.addView(icon(symbol,0xffe9e9ff),new LinearLayout.LayoutParams(dp(compact?28:34),dp(compact?28:34)));TextView label=text(title,compact?15:18,true);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(8),0,0);focusLabel(label);content.addView(label,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(compact?72:96),1);p.setMargins(dp(4),dp(3),dp(4),dp(3));row.addView(t,p);}
 private void dock(LinearLayout row,String title,int symbol,Runnable action){FrameLayout t=tile("dock-"+symbol,0xff17263c,action);t.setContentDescription(title);LinearLayout content=row();content.setDuplicateParentStateEnabled(true);content.setGravity(Gravity.CENTER);content.addView(icon(symbol,accent),new LinearLayout.LayoutParams(dp(23),dp(23)));TextView label=text(title,14,true);label.setPadding(dp(10),0,dp(10),0);content.addView(label);t.addView(content,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(4),dp(3),dp(4),dp(3));row.addView(t,p);}
 private List<AppCatalogue.App> pinned(){List<AppCatalogue.App> out=new ArrayList<>();Map<String,AppCatalogue.App> available=new LinkedHashMap<>();for(AppCatalogue.App a:catalogue)available.put(a.pkg,a);String saved=prefs().getString("launcher_pins","");if(!saved.isEmpty())try{JSONArray a=new JSONArray(saved);for(int i=0;i<Math.min(9,a.length());i++){AppCatalogue.App app=available.remove(a.optString(i));if(app!=null)out.add(app);}return out;}catch(Exception ignored){}for(String pkg:new String[]{"com.google.android.youtube.tv","com.netflix.ninja","com.tosmart.filemanagerpro","com.android.vending","com.droidlogic.tv.settings"}){AppCatalogue.App a=available.remove(pkg);if(a!=null)out.add(a);}for(AppCatalogue.App a:available.values())if(out.size()<9)out.add(a);return out;}
 private void paintApps(){if(appsGrid==null)return;appsGrid.removeAllViews();List<AppCatalogue.App> pins=pinned();int stamp=render;for(int y=0;y<2;y++){LinearLayout line=row();appsGrid.addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int x=0;x<5;x++){int index=y*5+x;AppCatalogue.App app=index<pins.size()?pins.get(index):null;boolean all=index==9;FrameLayout t=tile(all?"apps":app==null?"add-app":"app-"+app.pkg,all?0xff4d3d95:0xff29294b,()->{if(all)open(AppsActivity.class);else if(app==null)chooseApps();else try{startActivity(AppCatalogue.launch(app));}catch(Exception e){toast(tx("تعذر فتح التطبيق","Cannot open app"));}});t.setContentDescription(all?tx("كل التطبيقات","All apps"):app==null?tx("إضافة تطبيق","Add app"):app.label);LinearLayout content=col();content.setGravity(Gravity.CENTER);content.setDuplicateParentStateEnabled(true);content.setPadding(dp(5),dp(5),dp(5),dp(5));t.addView(content,new FrameLayout.LayoutParams(-1,-1));ImageView image=icon(all?HomeIcon.APPS:HomeIcon.PLUS,all?0xffd9cdff:accent);content.addView(image,new LinearLayout.LayoutParams(dp(compact?24:38),dp(compact?30:38)));TextView label=text(all?tx("كل التطبيقات","All apps"):app==null?tx("إضافة تطبيق","Add app"):app.label,compact?12:14,true);label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(0,dp(5),0,0);content.addView(label,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(t,p);if(app!=null&&!all){Bitmap b=icons.get(app.pkg);if(b!=null)image.setImageBitmap(b);else io.execute(()->{Bitmap bitmap=null;try{Drawable d=app.info.loadIcon(getPackageManager());bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);d.setBounds(0,0,96,96);d.draw(new Canvas(bitmap));icons.put(app.pkg,bitmap);}catch(Exception ignored){}Bitmap result=bitmap;main.post(()->{if(!destroyed&&stamp==render&&result!=null)image.setImageBitmap(result);});});}}}}
 private void chooseApps(){if(catalogue.isEmpty()){toast(tx("انتظر تحميل التطبيقات","Wait for the app list"));return;}Set<String> chosen=new LinkedHashSet<>();for(AppCatalogue.App a:pinned())chosen.add(a.pkg);String[] labels=new String[catalogue.size()];boolean[] checks=new boolean[labels.length];for(int i=0;i<labels.length;i++){labels[i]=catalogue.get(i).label;checks[i]=chosen.contains(catalogue.get(i).pkg);}new AlertDialog.Builder(this).setTitle(tx("اختر حتى 9 تطبيقات مفضلة","Choose up to 9 favourites")).setMultiChoiceItems(labels,checks,(dialog,index,on)->{String pkg=catalogue.get(index).pkg;if(on&&chosen.size()>=9){((AlertDialog)dialog).getListView().setItemChecked(index,false);toast(tx("يمكن تثبيت تسعة تطبيقات","You can pin nine apps"));}else if(on)chosen.add(pkg);else chosen.remove(pkg);}).setPositiveButton(tx("حفظ","Save"),(d,w)->{prefs().edit().putString("launcher_pins",new JSONArray(chosen).toString()).apply();paintApps();}).setNegativeButton(tx("إلغاء","Cancel"),null).show();}
 private void settings(){open(HubSettingsActivity.class);} private void legacySettings(){String[] labels={tx("إعدادات المساعد والترجمة","Companion and subtitles"),tx("مظهر شريط المعلومات","Information bar appearance"),tx("مصادر الصور والترجمة","Artwork and subtitle providers"),tx("تقرير توافق الجهاز","Device compatibility"),tx("اختيار الشاشة الرئيسية","Choose home app"),ar?"English":"العربية"};new AlertDialog.Builder(this).setTitle(tx("الإعدادات","Settings")).setItems(labels,(d,i)->{if(i==0)open(SatelliteActivity.class);else if(i==1)open(OsdSettingsActivity.class);else if(i==2)open(ProviderSetupActivity.class);else if(i==3)open(CompatibilityActivity.class);else if(i==4)try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){toast(tx("اختيار HOME غير متاح","Home selection unavailable"));}else{prefs().edit().putBoolean("arabic",!ar).apply();ar=!ar;build();}}).setNegativeButton(tx("إغلاق","Close"),null).show();}
 private void themes(){open(RxAppearanceActivity.class);}
 private void watch(){open(DvbPlaybackActivity.class);}
 private void open(Class<?> type){startActivity(new Intent(this,type));}
 private void update(){if(clock==null||destroyed)return;clock.setText(new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date()));ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities nc=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());boolean online=nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);connection.setText(online?tx("● متصل","● Online"):tx("● غير متصل","● Offline"));connection.setTextColor(online?0xff73dfbf:0xffd2ad8f);if(loading)return;loading=true;int stamp=render;io.execute(()->{SatelliteState s=ReceiverInfoRepository.get(this).current();main.post(()->{loading=false;if(destroyed||stamp!=render)return;channel.setText(s.channelName.isEmpty()?tx("قنواتك الفضائية بلمسة واحدة","Your satellite channels in one click"):tx("آخر قناة · ","Last channel · ")+s.channelName);programme.setText(s.title);String key=s.channelName+"|"+prefs().getLong("logo_mapping_changed_at",0);if(!logoKey.equals(key)){logoKey=key;channelLogo.setImageResource(R.drawable.channel_placeholder);if(!s.channelName.isEmpty())LogoRepository.get(this).request(s.channelName,s.channelId,new LogoRepository.Listener(){public void channel(Bitmap b){if(!destroyed&&stamp==render&&logoKey.equals(key)&&b!=null)channelLogo.setImageBitmap(b);}public void provider(String n,Bitmap b){}});}});});}
 @Override protected void onResume(){super.onResume();resumed=true;main.post(tick);}@Override protected void onPause(){resumed=false;main.removeCallbacks(tick);super.onPause();}
 @Override public void onBackPressed(){/* HOME remains IRAQhub. */}
 @Override protected void onDestroy(){destroyed=true;resumed=false;main.removeCallbacksAndMessages(null);io.shutdownNow();icons.evictAll();super.onDestroy();}
 private static final class HomeBackdrop extends Drawable {final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final int theme;HomeBackdrop(int t){theme=t;}public void draw(Canvas c){Rect r=getBounds();int glow=theme==1?0x22b18a42:theme==2?0x221b789b:0x222c4965;paint.setShader(new LinearGradient(0,0,r.width(),r.height(),new int[]{0xff070b12,0xff0b1420,0xff05080e},null,Shader.TileMode.CLAMP));c.drawRect(r,paint);paint.setShader(new RadialGradient(r.width()*.82f,r.height()*.1f,r.width()*.75f,new int[]{glow,0x00101525},null,Shader.TileMode.CLAMP));c.drawRect(r,paint);paint.setShader(null);}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}}
}
