package com.smarthub.plus;
import android.app.AlertDialog;import android.content.*;import android.graphics.*;import android.graphics.drawable.ColorDrawable;import android.os.*;import android.text.*;import android.view.*;import android.widget.*;import java.util.*;import java.util.concurrent.*;
/** Translucent channel menu over the independent decoder, backed by the real CHL catalogue. */
public class ReceiverLibraryActivity extends HubActivity {
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();private ReceiverCatalog catalog;private List<ReceiverCatalog.Channel> shown=Collections.emptyList();private List<ReceiverCatalog.Sat> satellites=Collections.emptyList();private List<ReceiverCatalog.Group> groups=Collections.emptyList();private Set<Long> favorites=Collections.emptySet();private Set<String> ambiguousPhysical=Collections.emptySet();private Map<String,SatelliteGuideRepository.Event> programmes=Collections.emptyMap();private Map<Long,Integer> satelliteNumbers=Collections.emptyMap();private String lastLiveTitle="";
 private EditText query;private ListView list;private TextView status,liveTitle,rf,signalDetail,focusedProgramme;private ProgressBar strength,quality;private Button satelliteButton,favoritesButton,previous,next;private boolean destroyed,loaded,favoritesOnly,alphabetical,initialPopup,encryptedOnly,wideList;private String initialFilter="";private long satellite=-1,activeId=-1,group=-1;private int type=-1,offset,generation,total,nextListSelection=-1,lastProgrammeBatch=-1;private long nextListFocusId=-1;private DirectReceiverSignal reader;
 private final Runnable filter=()->search(true);private final Runnable tick=new Runnable(){public void run(){if(!destroyed){refreshSignal();main.postDelayed(this,1000);}}};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
  ChannelScope saved=ChannelScope.read(this);
  type=getIntent().getIntExtra("type",saved.type<0?0:saved.type);
  satellite=getIntent().getLongExtra("satellite",saved.satellite);
  favoritesOnly=getIntent().getBooleanExtra("favorites",saved.favorites);
  encryptedOnly=getIntent().getBooleanExtra("encrypted_only",false);
  group=saved.group;alphabetical=saved.alphabetical;
  initialFilter=prefs().getString("receiver_initial_filter","");
  catalog=ReceiverCatalog.get(this);
  wideList=prefs().getBoolean("channel_list_wide_mode",false);
  FrameLayout backdrop=new FrameLayout(this);
  // Translucent RX side panel keeps the independent tuner video visible.
  backdrop.setBackgroundColor(0x36000000);
  backdrop.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
  LinearLayout root=col();
  root.setPadding(dp(18),dp(18),dp(18),dp(16));
  android.graphics.drawable.GradientDrawable tigerPanel=new android.graphics.drawable.GradientDrawable(
      android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
      new int[]{0xff050505,0xff000000});
   // Flush side list, with a subtle border rather than a floating card.
   tigerPanel.setCornerRadius(0);tigerPanel.setStroke(dp(1),0xff455563);
   root.setBackground(tigerPanel);
  int screenWidth=getResources().getDisplayMetrics().widthPixels;
  int targetWidth=Math.min(dp(wideList?900:570),
     (int)(screenWidth*(wideList?.78f:.52f)));
  FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(
     targetWidth,-1,wideList?Gravity.CENTER_HORIZONTAL:Gravity.LEFT);
  panel.setMargins(0,0,0,0);
  backdrop.addView(root,panel);

  LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
  TextView heading=text(tx("كل القنوات","ALL CHANNELS"),23,true);
  heading.setTextColor(0xfff1f1f5);
  top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  satelliteButton=tool(top,tx("◈ الأقمار","◈ Satellites"),this::satellites);
  satelliteButton.setTextColor(RxSkin.accent(this));
  satelliteButton.setTextSize(font(14));
  root.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));

  // 4.0.1 broadcast-first menu: no keyboard/search row on TV.
  query=new EditText(this);
  query.setSingleLine(true);
  query.setText("");
  LinearLayout tools=row();tools.setGravity(Gravity.CENTER_VERTICAL);
  favoritesButton=tool(tools,tx("★ المفضلة","★ Favourites"),this::groups);
  favoritesButton.setOnLongClickListener(v->{manageGroups();return true;});
  tool(tools,tx("🟨 الحروف","🟨 Letters"),this::showAlphabetIndex);
  tool(tools,wideList?tx("☰ جانبية","☰ Compact"):tx("▤ واسعة","▤ Wide"),this::toggleListMode);
  tool(tools,tx("⋯ المزيد","⋯ More"),this::showRxChannelActions);
  root.addView(tools,new LinearLayout.LayoutParams(-1,dp(49)));

  status=text(tx("قراءة القنوات…","Loading channels…"),11,false);
  status.setTextColor(MUTED);status.setPadding(dp(4),dp(5),0,dp(7));
  root.addView(status);
  list=new ListView(this);
  list.setBackgroundColor(0xff000000);
  list.setCacheColorHint(0xff000000);
  list.setScrollingCacheEnabled(false);
  list.setDividerHeight(dp(1));
  list.setDivider(new ColorDrawable(0x224a6070));
  android.graphics.drawable.GradientDrawable selected=new android.graphics.drawable.GradientDrawable();
   selected.setColor(0x442b3f39);selected.setStroke(dp(2),0xffffc755);
   list.setSelector(selected);list.setDrawSelectorOnTop(true);
   list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
  list.setOnItemClickListener((p,v,index,id)->play(shown.get(index)));
  list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
    updateFocusedProgramme(position);loadVisibleProgramme(position);
   }
   @Override public void onNothingSelected(AdapterView<?> parent){}
  });
  list.setOnItemLongClickListener((p,v,index,id)->{details(shown.get(index));return true;});
  root.addView(list,new LinearLayout.LayoutParams(-1,0,1));

  // Remove bulky lower meters and boxes; keep a single remote-key hint.
  liveTitle=text("",12,true);
  focusedProgramme=text("",11,false);
  signalDetail=text("",11,false);
  rf=text("",10,false);
  strength=meter(0xff22c7e9);quality=meter(0xff9cef26);
  TextView hint=text(tx("الأصفر: فلترة الأحرف • OK تشغيل • INFO التفاصيل",
    "YELLOW Letters • OK Play • INFO Details"),11,false);
  hint.setTextColor(0xffbacbd5);
  hint.setPadding(dp(8),dp(8),dp(8),dp(3));
  root.addView(hint);
  // Compact RF strip follows the highlighted channel; S/Q values are shown
  // only if the tuner is ACTUALLY tuned to that same physical service.
  LinearLayout frequencyStrip=col();
  frequencyStrip.setPadding(dp(8),dp(3),dp(8),dp(3));
  frequencyStrip.setBackgroundColor(0xff131c26);
  rf.setTextColor(0xffdfeaf2);rf.setSingleLine(true);
  rf.setEllipsize(android.text.TextUtils.TruncateAt.END);
  signalDetail.setTextColor(0xffa9c5dc);
  frequencyStrip.addView(rf,new LinearLayout.LayoutParams(-1,dp(19)));
  LinearLayout meters=row();meters.setGravity(Gravity.CENTER_VERTICAL);
  strength.setLayoutParams(new LinearLayout.LayoutParams(0,dp(5),1));
  quality.setLayoutParams(new LinearLayout.LayoutParams(0,dp(5),1));
  LinearLayout.LayoutParams qParams=new LinearLayout.LayoutParams(0,dp(5),1);
  qParams.leftMargin=dp(8);
  meters.addView(strength,new LinearLayout.LayoutParams(0,dp(5),1));
  meters.addView(quality,qParams);
  frequencyStrip.addView(meters,new LinearLayout.LayoutParams(-1,dp(8)));
  frequencyStrip.addView(signalDetail,new LinearLayout.LayoutParams(-1,dp(19)));
  root.addView(frequencyStrip,new LinearLayout.LayoutParams(-1,dp(56)));
  String initial=getIntent().getStringExtra("query");
  query.setText("");
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  backdrop.setFocusableInTouchMode(true);backdrop.requestFocus();
  setContentView(backdrop);
  query.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){
    main.removeCallbacks(filter);main.postDelayed(filter,160);
   }
   public void afterTextChanged(Editable e){}
  });
 }
 private void showRxChannelActions(){
  final String[] labels={
    tx("تصفية TV / Radio","Filter TV / Radio"),
    tx("عرض القنوات المشفرة","Encrypted channels"),
    tx("تشغيل/إيقاف توافق 4:2:2","Toggle 4:2:2 compatibility"),
    tx("التردد المحدد","Selected transponder"),
    tx("فلترة بالحروف","Filter by initial letter"),
    tx("دليل البرنامج المحدد","Selected programme EPG"),
    tx("تفاصيل القناة","Channel details"),
    tx("أدوات القنوات والاستيراد","Import and channel tools"),
    tx("إدارة مجموعات المفضلة","Manage favourite groups")
  };
  ReceiverPanelStyle.choose(this,tx("أدوات القنوات","Channel actions"),
    labels,-1,index->{
      switch(index){
       case 0:types();break;
       case 1:encryptedOnly=!encryptedOnly;type=0;favoritesOnly=false;group=-1;search(true);break;
       case 2:{
        boolean enabled=!prefs().getBoolean("dvb_422_compat_all",false);
        prefs().edit().putBoolean("dvb_422_compat_all",enabled).apply();
        toast(enabled?"4:2:2 ✓":"4:2:2 OFF");break;
       }
       case 3:focusedTransponder();break;
       case 4:showAlphabetIndex();break;
       case 5:focusedGuide();break;
       case 6:focusedDetails();break;
       case 7:startActivity(new Intent(this,ReceiverToolsActivity.class));break;
       case 8:manageGroups();break;
      }
    });
 }
 private void toggleListMode(){
  ReceiverCatalog.Channel selected=focusedChannel();
  prefs().edit().putBoolean("channel_list_wide_mode",!wideList)
   .putLong("channel_list_restore_focus_id",selected==null?-1:selected.id)
   .putInt("channel_list_restore_focus_page",offset).apply();
  recreate();
 }
 private ReceiverCatalog.Channel focusedChannel(){
  int selected=list==null?-1:list.getSelectedItemPosition();
  return selected>=0&&selected<shown.size()?shown.get(selected):null;
 }
 private void focusSearch(){
  if(query==null)return;
  query.requestFocus();
  android.view.inputmethod.InputMethodManager manager=
   (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
  if(manager!=null)manager.showSoftInput(query,
    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
 }
 private void focusedDetails(){
  ReceiverCatalog.Channel channel=focusedChannel();
  if(channel!=null)details(channel);
 }
 private void focusedTransponder(){
  ReceiverCatalog.Channel channel=focusedChannel();
  if(channel==null){toast(tx("اختر قناة أولاً","Select a channel first"));return;}
  new AlertDialog.Builder(this).setTitle(channel.name)
   .setMessage(channel.rf()+"\\nSID "+channel.sid+" · "+channel.provider)
   .setPositiveButton(tx("بحث يدوي على هذا التردد","Manual scan this TP"),
    (dialog,which)->startActivity(new Intent(this,ManualTransponderActivity.class)
       .putExtra("satellite",(long)channel.satId)
       .putExtra("frequency",channel.frequency)
       .putExtra("symbol_rate",channel.symbolRate)
       .putExtra("polarity",channel.pol)))
   .setNegativeButton(tx("إغلاق","Close"),null).show();
 }
 private void focusedGuide(){
  ReceiverCatalog.Channel channel=focusedChannel();
  if(channel==null)return;
  SatelliteGuideRepository.Event event=programmes.get("DVB:"+channel.physical());
  String detail=event==null?tx("لا توجد بيانات EPG مؤكدة لهذه القناة",
      "No verified guide data for this channel"):
      event.title+"\\n"+new java.text.SimpleDateFormat("HH:mm",
        java.util.Locale.ROOT).format(new java.util.Date(event.startMs))+
      "–"+new java.text.SimpleDateFormat("HH:mm",
        java.util.Locale.ROOT).format(new java.util.Date(event.endMs));
  new AlertDialog.Builder(this).setTitle(channel.name)
   .setMessage(detail).setPositiveButton("OK",null).show();
 }
 private void updateFocusedProgramme(int position){
  if(focusedProgramme==null||position<0||position>=shown.size())return;
  ReceiverCatalog.Channel channel=shown.get(position);
  if(wideList){
   focusedProgramme.setText(channel.name+" · "+channel.rf());
  }else{
   SatelliteGuideRepository.Event event=programmes.get("DVB:"+channel.physical());
   focusedProgramme.setText(tx("دليل المحددة: ","Selected EPG: ")+
    (event==null?tx("غير متاح","Unavailable"):event.title));
  }
 }
 private Button tool(LinearLayout row,String label,Runnable click){Button b=button(label);b.setTextSize(font(11));b.setMinHeight(0);b.setPadding(dp(4),0,dp(4),0);b.setBackground(ReceiverPanelStyle.row(this));b.setOnClickListener(v->click.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(30),1);row.addView(b,p);return b;}
 private ProgressBar meter(int color){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setProgressTintList(android.content.res.ColorStateList.valueOf(color));p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x775c6270));return p;}
 @Override protected void onResume(){super.onResume();reader=ReceiverInfoRepository.get(this).acquireSignal();main.post(tick);int token=++generation;ChannelScope browse=scope();io.execute(()->{try{catalog.ensure();List<ReceiverCatalog.Sat> sats=catalog.satellites();List<ReceiverCatalog.Group> fav=catalog.groups();int count=catalog.count();Map<Long,Integer> numbers=catalog.satelliteNumbers();Set<String> collisions=catalog.ambiguousPhysicalKeys();ReceiverCatalog.Channel active=catalog.channel(prefs().getLong("own_dvb_channel",-1));ChannelScope base=catalog.validScope(browse);
 ChannelScope checked=!loaded&&base.satellite<0&&active!=null&&!getIntent().hasExtra("satellite")?
  new ChannelScope(base.query,active.satId,base.type,base.favorites,base.alphabetical,base.group):base;int activePage=0;main.post(()->{if(destroyed||token!=generation)return;satellites=sats;groups=fav;satelliteNumbers=numbers;ambiguousPhysical=collisions;total=count;satellite=checked.satellite;group=checked.group;satelliteButton.setText(tx("‹  كل الأقمار  ›","‹  All satellites  ›"));favoritesButton.setText(favoritesOnly?tx("مفضلتي في IRAQhub","My IRAQhub favourites"):tx("المفضلة","Favourites"));boolean anchor=!loaded&&active!=null&&(type<0||active.tv==type)&&SatelliteState.read(this).channelId.equals("DVB:"+active.physical());
long restoreId=prefs().getLong("channel_list_restore_focus_id",-1);
int restorePage=prefs().getInt("channel_list_restore_focus_page",-1);
loaded=true;for(ReceiverCatalog.Sat sat:sats)if(sat.id==satellite)satelliteButton.setText("‹  "+sat.name+"  ›");if(group>=0)for(ReceiverCatalog.Group g:fav)if(g.id==group)favoritesButton.setText(g.name);if(restoreId>=0&&restorePage>=0){
   offset=0;nextListFocusId=restoreId;
   prefs().edit().remove("channel_list_restore_focus_id")
    .remove("channel_list_restore_focus_page").apply();
   search(false);
  }else if(anchor){offset=0;nextListFocusId=active.id;search(false);}else search(true);if(!initialPopup){initialPopup=true;if(getIntent().getBooleanExtra("satellite_popup",false))satellites();else if(getIntent().getBooleanExtra("favorites",false))groups();}});}catch(Exception unavailable){main.post(()->{if(!destroyed)status.setText(tx("تعذر الفهرس؛ استورد CHL","Import CHL to restore the catalogue"));});}});}
 @Override protected void onPause(){main.removeCallbacks(tick);if(reader!=null){ReceiverInfoRepository.get(this).releaseSignal(reader);reader=null;}super.onPause();}
 private ChannelScope scope(){return new ChannelScope(query.getText().toString(),satellite,type,favoritesOnly,alphabetical,group);}
 private void pageChannels(int direction){pageChannels(direction,10);}
 private void pageChannels(int direction,int jump){
  int n=shown.size();if(n==0)return;
  int selected=Math.max(0,list.getSelectedItemPosition());
  list.requestFocus();
  list.setSelection(CircularChannelNavigation.move(selected,direction*jump,n));
 }
 /** YELLOW opens exact first-letter filters, not a transient index jump. */
 private void showAlphabetIndex(){
  final String[] letters=ChannelInitialFilter.choices();
  String[] labels=new String[letters.length];
  int selected=0;
  for(int i=0;i<letters.length;i++){
   labels[i]=letters[i].isEmpty()?tx("كل القنوات","All channels"):letters[i];
   if(letters[i].equals(initialFilter))selected=i;
  }
  ReceiverPanelStyle.chooseLetters(this,tx("الحروف • الأصفر","Letters • Yellow"),labels,
    selected,index->{
     initialFilter=letters[index];
     prefs().edit().putString("receiver_initial_filter",initialFilter).apply();
     search(true);
  });
 }
 /** Read guide data around the highlighted row, not for the entire satellite. */
 private String lastLogoPrefetch="";
 private long lastLogoPrefetchAt;
 private void prefetchFocusedLogo(int position){
  if(position<0||position>=shown.size()||destroyed)return;
  ReceiverCatalog.Channel channel=shown.get(position);
  String identity="DVB:"+channel.physical();
  long now=android.os.SystemClock.elapsedRealtime();
  if(identity.equals(lastLogoPrefetch)&&now-lastLogoPrefetchAt<45000)return;
  lastLogoPrefetch=identity;lastLogoPrefetchAt=now;
  // Online source waterfalls are allowed only for the SELECTED channel and
  // never block the D-Pad UI; a verified result repaints the 1 visible row.
  LogoRepository.get(this).request(channel.name,identity,new LogoRepository.Listener(){
   @Override public void channel(android.graphics.Bitmap image){
    if(image!=null&&identity.equals(lastLogoPrefetch)&&!destroyed)
      main.post(()->{if(!destroyed&&list!=null)list.invalidateViews();});
   }
   @Override public void provider(String name,android.graphics.Bitmap image){}
  });
 }
 private void loadVisibleProgramme(int position){
  prefetchFocusedLogo(position);
  int batch=position/18;if(batch==lastProgrammeBatch||shown.isEmpty())return;
  lastProgrammeBatch=batch;
  int from=Math.max(0,position-9),until=Math.min(shown.size(),position+20);
  Set<String> ids=new HashSet<>();
  for(int i=from;i<until;i++)ids.add("DVB:"+shown.get(i).physical());
  int token=generation;
  io.execute(()->{
   Map<String,SatelliteGuideRepository.Event> found=
     new SatelliteGuideRepository(this).currentChannels(ids,System.currentTimeMillis());
   main.post(()->{
    if(destroyed||generation!=token||found.isEmpty())return;
    Map<String,SatelliteGuideRepository.Event> all=new HashMap<>(programmes);
    all.putAll(found);programmes=all;list.invalidateViews();
    updateFocusedProgramme(list.getSelectedItemPosition());
   });
  });
 }
 private void search(boolean reset){if(!loaded)return;if(reset)offset=0;scope().save(this);String q=query.getText().toString();long sat=satellite,favGroup=group;int kind=type,page=offset,token=++generation;boolean fav=favoritesOnly,alpha=alphabetical;io.execute(()->{try{List<ReceiverCatalog.Channel> source=catalog.channelsContinuous(q,sat,kind,fav,alpha,favGroup,encryptedOnly);
 String initial=initialFilter;
 List<ReceiverCatalog.Channel> rows;
 if(initial.isEmpty())rows=source;
 else {
  rows=new ArrayList<>();
  for(ReceiverCatalog.Channel ch:source)
   if(ChannelInitialFilter.matches(ch.name,initial))rows.add(ch);
 }
 Set<Long> selected=catalog.favorites();Set<String> physical=new HashSet<>();for(int i=0;i<Math.min(rows.size(),96);i++)physical.add("DVB:"+rows.get(i).physical());long epgNow=System.currentTimeMillis();
 // FIX: Do not block showing the channel list on a 96-channel EPG database
 // query. Render names/CA/signal immediately; fetch EPG only for visible rows.
 Map<String,SatelliteGuideRepository.Event> events=new HashMap<>();
 long current=prefs().getLong("own_dvb_channel",-1);if(!SatelliteState.read(this).channelId.startsWith("DVB:"))current=catalog.uniqueChannel(SatelliteState.read(this).channelName);long playing=current;main.post(()->{if(destroyed||token!=generation)return;shown=rows;favorites=selected;programmes=events;activeId=playing;status.setText((sat<0?tx("كل الأقمار","All satellites"):satelliteButton.getText())+
   (encryptedOnly?tx(" · مشفرة فقط"," · encrypted only"):"")+
   tx("  ·  من ","  ·  positions ")+(rows.isEmpty()?"0":"1–"+rows.size())+(alphabetical?"  A–Z":"")+
   (initial.isEmpty()?"":tx("  · حرف ","  · letter ")+initial));lastProgrammeBatch=-1;list.setAdapter(new Rows());if(!rows.isEmpty())updateFocusedProgramme(
   Math.min(Math.max(list.getSelectedItemPosition(),0),rows.size()-1));
   if(!query.hasFocus()){
     list.requestFocus();
     if(nextListFocusId>=0){
       long focus=nextListFocusId;nextListFocusId=-1;
       boolean restored=false;
       for(int i=0;i<rows.size();i++)if(rows.get(i).id==focus){
        list.setSelection(i);restored=true;break;
       }
       if(!restored&&nextListSelection>=0)list.setSelection(Math.min(
        nextListSelection,Math.max(0,rows.size()-1)));
       nextListSelection=-1;
     }else if(nextListSelection>=0){
       list.setSelection(Math.min(nextListSelection,Math.max(0,rows.size()-1)));
       nextListSelection=-1;
     }else for(int i=0;i<rows.size();i++)if(rows.get(i).id==activeId){
       list.setSelection(i);break;
     }
    }});}catch(Exception failure){main.post(()->{if(!destroyed&&token==generation)status.setText(tx("تعذر البحث في الفهرس","Could not search the catalogue"));});}});}
 private void satellites(){String[] labels=new String[satellites.size()+1];labels[0]=tx("كل الأقمار","All satellites");int selected=0;for(int i=0;i<satellites.size();i++){labels[i+1]=satellites.get(i).label();if(satellite==satellites.get(i).id)selected=i+1;}ReceiverPanelStyle.choose(this,tx("قائمة الأقمار الصناعية","Satellite list"),labels,selected,index->{satellite=index==0?-1:satellites.get(index-1).id;satelliteButton.setText("‹  "+labels[index]+"  ›");query.setText("");search(true);});}
 private void groups(){String[] labels=new String[groups.size()+3];labels[0]=tx("الكل","All");labels[1]=tx("مفضلتي في IRAQhub","My IRAQhub favourites");int selected=favoritesOnly?1:0;for(int i=0;i<groups.size();i++){ReceiverCatalog.Group g=groups.get(i);labels[i+2]=g.name+"  ("+g.count+")";if(group==g.id)selected=i+2;}labels[labels.length-1]=tx("إدارة مجموعات المفضلة…","Manage favourite groups…");ReceiverPanelStyle.choose(this,tx("مجموعات المفضلة","Channel Favorites"),labels,selected,index->{if(index==labels.length-1){manageGroups();return;}query.setText("");favoritesOnly=index==1;group=index<2?-1:groups.get(index-2).id;satellite=-1;satelliteButton.setText(tx("‹  كل الأقمار  ›","‹  All satellites  ›"));favoritesButton.setText(index<2?labels[index]:groups.get(index-2).name);search(true);});}

 private void reloadGroups(){if(destroyed||io.isShutdown())return;io.execute(()->{List<ReceiverCatalog.Group> fresh=catalog.groups();main.post(()->{if(!destroyed){groups=fresh;search(false);}});});}
 private void manageGroups(){String[] labels=new String[groups.size()+1];labels[0]=tx("+ إنشاء مجموعة","+ New group");for(int n=0;n<groups.size();n++)labels[n+1]=groups.get(n).name;ReceiverPanelStyle.choose(this,tx("إدارة المفضلة","Manage favourites"),labels,0,index->{if(index==0)nameGroup(null);else{ReceiverCatalog.Group chosen=groups.get(index-1);ReceiverPanelStyle.choose(this,chosen.name,new String[]{tx("إعادة تسمية","Rename"),tx("حذف المجموعة","Delete group")},0,action->{if(action==0)nameGroup(chosen);else new AlertDialog.Builder(this).setTitle(tx("حذف المجموعة؟","Delete group?")).setMessage(chosen.name).setNegativeButton(tx("إلغاء","Cancel"),null).setPositiveButton(tx("حذف","Delete"),(d,w)->io.execute(()->{catalog.deleteGroup(chosen.id);main.post(()->{if(group==chosen.id){group=-1;favoritesButton.setText(tx("المفضلة","Favourites"));}reloadGroups();});})).show();});}});}
 private void nameGroup(ReceiverCatalog.Group existing){EditText name=new EditText(this);name.setSingleLine(true);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});if(existing!=null)name.setText(existing.name);new AlertDialog.Builder(this).setTitle(existing==null?tx("إنشاء مجموعة","New group"):tx("إعادة تسمية","Rename")).setView(name).setNegativeButton(tx("إلغاء","Cancel"),null).setPositiveButton(tx("حفظ","Save"),(d,w)->{String value=name.getText().toString();io.execute(()->{try{if(existing==null)catalog.createGroup(value);else catalog.renameGroup(existing.id,value);main.post(this::reloadGroups);}catch(IllegalArgumentException invalid){main.post(()->toast(tx("أدخل اسماً صحيحاً للمجموعة","Enter a valid group name")));}});}).show();}
 private void memberships(ReceiverCatalog.Channel channel){io.execute(()->{List<ReceiverCatalog.Group> fresh=catalog.groups();boolean[] enabled=new boolean[fresh.size()];for(int n=0;n<fresh.size();n++)enabled[n]=catalog.member(fresh.get(n).id,channel.id);main.post(()->{if(destroyed)return;if(fresh.isEmpty()){nameGroup(null);return;}String[] names=new String[fresh.size()];for(int n=0;n<names.length;n++)names[n]=fresh.get(n).name;new AlertDialog.Builder(this).setTitle(channel.name).setMultiChoiceItems(names,enabled,(d,index,checked)->enabled[index]=checked).setPositiveButton(tx("حفظ","Save"),(d,w)->io.execute(()->{for(int n=0;n<fresh.size();n++)catalog.member(fresh.get(n).id,channel.id,enabled[n]);main.post(this::reloadGroups);})).setNegativeButton(tx("إلغاء","Cancel"),null).show();});});}
 private void types(){ReceiverPanelStyle.choose(this,tx("نوع القنوات","Channel type"),new String[]{tx("الكل","All"),"TV",tx("راديو","Radio"),tx("غير منشور","Unknown")},Math.max(0,type+1),index->{type=index-1;search(true);});}
 private void favorite(ReceiverCatalog.Channel ch){boolean on=!favorites.contains(ch.id);io.execute(()->{catalog.favorite(ch.id,on);main.post(()->search(false));});}
 private void play(ReceiverCatalog.Channel ch){
  if(SafePlaybackMode.enabled(this)){playHardware(ch);return;}
  if((prefs().getBoolean("dvb_422_compat_all",false)||
    getIntent().getBooleanExtra("prefer_422_engine",false))&&ch.ca>=0&&ch.tv==0)
   playSoftware(ch);
  else playHardware(ch);
 }
 private void playHardware(ReceiverCatalog.Channel ch){
  new ChannelScope("",satellite,ch.tv,favoritesOnly,alphabetical,group).save(this);
  startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",ch.id));
  finish();
 }
 private void playSoftware(ReceiverCatalog.Channel ch){
  if(ch.tv!=0||ch.ca<0){
   toast(tx("يتطلب تشغيل 4:2:2 قناة تلفزيونية ببيانات DVB صالحة",
      "4:2:2 requires a DVB TV service with complete metadata"));
   return;
  }
  if(ch.ca!=0){
   new AlertDialog.Builder(this).setTitle(tx("تجربة بث مشفّر 4:2:2","Try encrypted 4:2:2"))
    .setMessage(tx("سيُستخدم فك BISS/CA المصرّح الموجود في الرسيفر فقط. إذا خرجت حزم TS مشفرة سيوقفها IRAQhub؛ مفتاح BISS المحلي لا يُحقن في خدمات المصنع.",
      "Uses only the receiver's existing authorised CA/BISS handling. If captured video is still scrambled, IRAQhub will reject it. Local BISS keys are not injected into firmware."))
    .setPositiveButton(tx("تجربة","Try"),(dialog,which)->choose422Engine(ch))
    .setNegativeButton(tx("إلغاء","Cancel"),null).show();
   return;
  }
  choose422Engine(ch);
 }
 private void choose422Engine(ReceiverCatalog.Channel ch){
  // One remote-facing player. Internally recover between VLC and mpv only
  // when needed; do not remove unverified fallback classes from the APK.
  prefs().edit().putString("decoder_422_engine","unified").apply();
  launchSoftware(ch,"unified");
 }
 private void launchSoftware(ReceiverCatalog.Channel ch,String engine){
  new ChannelScope("",satellite,ch.tv,favoritesOnly,alphabetical,group).save(this);
  startActivity(new Intent(this,
    ("isolated".equals(engine)||Native422V2Profile.enabled(engine))?
      Independent422Activity.class:SoftwareVideoActivity.class)
   .putExtra("dvb_channel",ch.id).putExtra("decoder_engine",engine)
   .putExtra("video_only",prefs().getBoolean("independent_422_video_only",true)));
  finish();
 }
 private void details(ReceiverCatalog.Channel ch){
  String[] items=new String[]{
    tx("تشغيل القناة","Play with normal decoder"),
    tx("تشغيل IRAQhub 422 الموحد","Unified IRAQhub 422"),
    favorites.contains(ch.id)?tx("إزالة من المفضلة","Remove from favourites"):tx("إضافة للمفضلة","Add to favourites"),
    tx("مجموعات القناة","Channel groups"),
    tx("معلومات القناة","Channel details"),
    tx("إعدادات القناة داخل IRAQhub","IRAQhub channel settings"),
    tx("LNB وDiSEqC","LNB and DiSEqC"),
    tx("الموتور وUSALS","Motor / USALS"),
    tx("جعلها قناة بدء تشغيل IRAQhub","Set as IRAQhub startup channel"),
    tx("نقل القناة للأعلى بقائمتي","Move channel up in my list"),
    tx("نقل القناة للأسفل بقائمتي","Move channel down in my list"),
    tx("حذف القناة من قائمتي فقط","Delete channel from my local list")
  };
  ReceiverPanelStyle.choose(this,ch.name+" · "+ch.rf(),items,0,index->{
   switch(index){
    case 0:playHardware(ch);break;
    case 1:playSoftware(ch);break;
    case 2:favorite(ch);break;
    case 3:memberships(ch);break;
    case 5:ChannelControls.open(this,ch);break;
    case 6:startActivity(new Intent(this,TunerSettingsActivity.class).putExtra("satellite",(long)ch.satId));break;
    case 7:startActivity(new Intent(this,MotorCenterActivity.class).putExtra("satellite",(long)ch.satId));break;
    case 8:
     prefs().edit().putBoolean("own_dvb_startup_enabled",true)
      .putLong("own_dvb_startup",ch.id).apply();
     toast(tx("تم تعيين قناة البدء في IRAQhub","IRAQhub startup channel saved"));break;
    case 9:moveLocal(ch,-1);break;
    case 10:moveLocal(ch,1);break;
    case 11:deleteLocal(ch);break;
    default:new AlertDialog.Builder(this).setTitle(ch.name)
       .setMessage(ch.provider+"\\n"+ch.rf()+"\\nSID "+ch.sid+" · "+
         (ch.ca<0?"—":ch.ca==0?"FTA":tx("مشفرة","Encrypted")))
       .setPositiveButton("OK",null).show();
   }
  });
 }
 private void moveLocal(ReceiverCatalog.Channel channel,int direction){
  io.execute(()->{
   boolean changed=catalog.moveLocal(channel.id,direction);
   main.post(()->{
    if(destroyed)return;
    if(changed)search(false);
    else toast(tx("القناة في بداية أو نهاية القائمة","Channel is already at the list boundary"));
   });
  });
 }
 private void deleteLocal(ReceiverCatalog.Channel channel){
  new AlertDialog.Builder(this).setTitle(tx("حذف من قائمة IRAQhub","Delete from IRAQhub catalogue"))
    .setMessage(tx("سيُحذف هذا السجل من قاعدة IRAQhub فقط؛ قاعدة قنوات Aurum والروم لا تتغير. يمكن إعادة استيراده من ملف CHL إذا احتجته.",
       "Only the IRAQhub catalogue entry will be deleted. The firmware database is unchanged. Reimport CHL to restore it.")+
       "\n"+channel.name+" · "+channel.rf())
    .setPositiveButton(tx("حذف","Delete"),(d,w)->io.execute(()->{
      boolean deleted=catalog.deleteLocal(channel.id);
      if(deleted&&prefs().getLong("own_dvb_startup",-1)==channel.id)
       prefs().edit().remove("own_dvb_startup").putBoolean("own_dvb_startup_enabled",false).apply();
      main.post(()->{
       if(destroyed)return;
       toast(deleted?tx("حُذفت من قائمتي","Removed from local list"):
          tx("ما أگدر أحذف آخر قناة","Cannot delete the final channel"));
       search(false);
      });
    }))
    .setNegativeButton(tx("إلغاء","Cancel"),null).show();
 }
 private void refreshSignal(){
  SatelliteState s=SatelliteState.read(this);
  if(reader!=null)reader.refresh(s);
  ReceiverInfoRepository.Info info=ReceiverInfoRepository.get(this).info(s);
  liveTitle.setText(s.channelName);
  String liveKey=s.channelId+"|"+s.title+"|"+(System.currentTimeMillis()/30000);
  if(!lastLiveTitle.equals(liveKey)){lastLiveTitle=liveKey;list.invalidateViews();}
  ReceiverCatalog.Channel selected=focusedChannel();
  rf.setText(selected==null?tx("اختر قناة لعرض التردد","Select a channel to see RF"):
    selected.frequency+" "+selected.pol+" / "+selected.symbolRate+" · "+selected.satName);
  boolean tuned=selected!=null&&s.channelId.equals("DVB:"+selected.physical());
  strength.setVisibility(tuned&&!info.strength.isEmpty()?View.VISIBLE:View.INVISIBLE);
  quality.setVisibility(tuned&&!info.quality.isEmpty()?View.VISIBLE:View.INVISIBLE);
  strength.setProgress(tuned?percent(info.strength):0);
  quality.setProgress(tuned?percent(info.quality):0);
  signalDetail.setText(tuned?
     "S: "+(info.strength.isEmpty()?"—":info.strength)+
     "   Q: "+(info.quality.isEmpty()?"—":info.quality)+
     "   "+info.signalDetail():
     tx("S/Q — الإشارة الحالية ليست للقناة المحددة",
       "S/Q — tuner not on selected channel"));
 }
 private static int percent(String s){try{return (int)Float.parseFloat(s.replace("%",""));}catch(Exception no){return 0;}}
 private final class ChannelRow {
  LinearLayout container;
  TextView number,name,programme,badge;
  ImageView logo,provider;
 }
 /** Recycle Tiger channel rows. Bind new text/identity to the existing views
  * instead of allocating several layouts, bitmaps and drawables on every scroll. */
 private final class Rows extends BaseAdapter {
  private final SatelliteState active=ReceiverInfoRepository.get(ReceiverLibraryActivity.this).current();
  @Override public boolean hasStableIds(){return true;}
  @Override public int getCount(){return shown.size();}
  @Override public Object getItem(int position){return shown.get(position);}
  @Override public long getItemId(int position){return shown.get(position).id;}
  @Override public View getView(int position,View old,ViewGroup parent){
   ReceiverCatalog.Channel ch=shown.get(position);
   String identity=ch.physical();
   ChannelRow h;
   if(old!=null&&old.getTag() instanceof ChannelRow){
    h=(ChannelRow)old.getTag();
   }else{
    h=new ChannelRow();
    LinearLayout row=row();
    h.container=row;
    row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    row.setPadding(dp(5),dp(3),dp(8),dp(3));
    row.setBackgroundColor(0xff000000); // Tiger black channel list; focus border uses selector.
    int screen=(int)(getResources().getDisplayMetrics().heightPixels/
       getResources().getDisplayMetrics().density);
    int height=Math.max(Math.round(font(17)+font(11)+12),
       Math.min(72,list.getHeight()>0?(int)(list.getHeight()/
         getResources().getDisplayMetrics().density/8):(screen-260)/8));
    row.setLayoutParams(new AbsListView.LayoutParams(-1,dp(height)));
    h.number=text("",16,false);
    h.number.setTextColor(0xff48dc64);
    row.addView(h.number,new LinearLayout.LayoutParams(dp(38),-2));
    h.logo=new ImageView(ReceiverLibraryActivity.this);
    h.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    LinearLayout logoStack=col();
    logoStack.setGravity(Gravity.CENTER);
    logoStack.addView(h.logo,new LinearLayout.LayoutParams(dp(48),dp(31)));
    h.provider=new ImageView(ReceiverLibraryActivity.this);
    h.provider.setScaleType(ImageView.ScaleType.FIT_CENTER);
    logoStack.addView(h.provider,new LinearLayout.LayoutParams(dp(23),dp(10)));
    row.addView(logoStack,new LinearLayout.LayoutParams(dp(52),-2));
    LinearLayout info=col();
    info.setPadding(dp(10),0,dp(6),0);
    row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
    h.name=text("",17,false);
    h.name.setTextColor(0xff40df4a);
    h.name.setGravity(Gravity.LEFT);
    h.name.setSingleLine(true);
    h.name.setEllipsize(TextUtils.TruncateAt.END);
    info.addView(h.name);
    h.programme=text("",11,false);
    h.programme.setSingleLine(true);
    h.programme.setEllipsize(TextUtils.TruncateAt.END);
    info.addView(h.programme);
    h.badge=text("",10,true);
    h.badge.setGravity(Gravity.CENTER);
    row.addView(h.badge,new LinearLayout.LayoutParams(dp(51),dp(21)));
    row.setTag(h);
   }
   h.logo.setTag(identity);
   h.logo.setImageResource(R.drawable.channel_placeholder);
   h.provider.setImageDrawable(null);
   h.provider.setVisibility(View.GONE);
   h.number.setText(Integer.toString(satelliteNumbers.getOrDefault(ch.id,position+offset+1)));
   h.name.setText((favorites.contains(ch.id)?"★ ":"")+ch.name);
   boolean encrypted=ch.ca>0; // CA=0 FTA, CA<0 unknown: do not guess.
   h.name.setTextColor(encrypted?0xff35e84c:0xfff0f0f1);
   h.number.setTextColor(encrypted?0xff35e84c:0xffcad4db);
   boolean current=ch.id==activeId&&active.channelName.equalsIgnoreCase(ch.name);
   SatelliteGuideRepository.Event cached=programmes.get("DVB:"+identity);
   long now=System.currentTimeMillis();
   String title=current&&!active.title.isEmpty()?active.title:
     cached!=null&&cached.startMs<=now&&cached.endMs>now?cached.title:"";
   h.programme.setText(title.isEmpty()?tx("لا توجد بيانات برنامج","No programme information"):title);
   h.programme.setTextColor(current?0xff75f5ad:MUTED);
   String badge=current?ResolutionBadge.label(ReceiverLibraryActivity.this,active):"";
   if(badge.isEmpty())h.badge.setVisibility(View.GONE);
   else{
    h.badge.setVisibility(View.VISIBLE);
    h.badge.setText(badge);
    h.badge.setBackgroundResource(ResolutionBadge.asset(badge));
    h.badge.setTextColor(ResolutionBadge.color(badge));
   }
   if(ambiguousPhysical.contains(identity)){
    h.logo.setContentDescription(tx("هوية DVB مكررة · الشعار محجوب لتجنب الخطأ",
      "Ambiguous DVB identity · logo omitted to avoid wrong association"));
   }else{
    final ImageView logo=h.logo,provider=h.provider;
    final String expected=identity;
    LogoRepository.Listener listener=new LogoRepository.Listener(){
     @Override public void channel(Bitmap image){
      if(!destroyed&&expected.equals(logo.getTag())&&image!=null)logo.setImageBitmap(image);
     }
     @Override public void provider(String name,Bitmap image){
      if(!destroyed&&expected.equals(logo.getTag())){
       provider.setImageBitmap(image);
       provider.setContentDescription(name);
       provider.setVisibility(image==null?View.GONE:View.VISIBLE);
      }
     }
    };
    // Scrolling must NEVER start multi-site HTTPS searches for each transient row.
    // Visible cached PNG/verified receiver icons can load asynchronously only.
    LogoRepository.get(ReceiverLibraryActivity.this).requestCached(ch.name,"DVB:"+identity,listener);
   }
   return h.container;
  }
 }
 @Override public boolean dispatchKeyEvent(KeyEvent e){int k=e.getKeyCode();
  if(k==KeyEvent.KEYCODE_GUIDE||k==KeyEvent.KEYCODE_SEARCH||k==KeyEvent.KEYCODE_MENU){
   if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
    if(k==KeyEvent.KEYCODE_GUIDE)focusedGuide();
    else if(k==KeyEvent.KEYCODE_SEARCH)showAlphabetIndex();
    else focusedDetails();
   }
   return true;
  }
  if(!query.hasFocus()&&list.hasFocus()&&
      (k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN)&&
      e.getAction()==KeyEvent.ACTION_DOWN&&!shown.isEmpty()){
    int position=list.getSelectedItemPosition();
    if(k==KeyEvent.KEYCODE_DPAD_UP&&position<=0){list.setSelection(shown.size()-1);return true;}
    if(k==KeyEvent.KEYCODE_DPAD_DOWN&&position==shown.size()-1){list.setSelection(0);return true;}
  }
  if(k==KeyEvent.KEYCODE_PAGE_DOWN){
   if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
    int n=list.getSelectedItemPosition();
    ReceiverCatalog.Channel selected=n>=0&&n<shown.size()?shown.get(n):null;
    if(selected==null)selected=catalog.channel(prefs().getLong("own_dvb_channel",-1));
    if(selected!=null)BissKeyDialog.show(this,selected);
    else toast(tx("اختر قناة فضائية أولاً","Select a DVB channel"));
   }return true;
  }
  if(k==KeyEvent.KEYCODE_PAGE_UP){
   if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
    encryptedOnly=!encryptedOnly;type=0;favoritesOnly=false;group=-1;search(true);
   }return true;
  }
  if((k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT)&&!query.hasFocus()){
   if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)
     pageChannels(k==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);
   return true;
  }
  if(k==17||k==237||k==34||k==440||k==KeyEvent.KEYCODE_TV_RADIO_SERVICE||k==170||k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_CHANNEL_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){if(k==17||k==237)satellites();else if(k==34||k==440)groups();else if(k==KeyEvent.KEYCODE_TV_RADIO_SERVICE||k==170){type=type==1?0:1;search(true);}else pageChannels(k==KeyEvent.KEYCODE_CHANNEL_UP?1:-1,1);}return true;}if(k==KeyEvent.KEYCODE_PROG_RED||k==KeyEvent.KEYCODE_PROG_GREEN||k==KeyEvent.KEYCODE_PROG_YELLOW||k==KeyEvent.KEYCODE_PROG_BLUE||k==KeyEvent.KEYCODE_INFO){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){if(k==KeyEvent.KEYCODE_PROG_RED)groups();else if(k==KeyEvent.KEYCODE_PROG_GREEN)types();else if(k==KeyEvent.KEYCODE_PROG_YELLOW)showAlphabetIndex();else if(k==KeyEvent.KEYCODE_PROG_BLUE)startActivity(new Intent(this,SignalInformationActivity.class));else{int p=list.getSelectedItemPosition();if(p>=0&&p<shown.size())details(shown.get(p));}}return true;}return super.dispatchKeyEvent(e);}
 @Override public void onBackPressed(){
  if(!initialFilter.isEmpty()){
   initialFilter="";
   prefs().edit().remove("receiver_initial_filter").apply();
   search(true);
  }else super.onBackPressed();
 }
 @Override protected void onDestroy(){destroyed=true;generation++;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
