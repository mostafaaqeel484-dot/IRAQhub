package com.smarthub.plus;
import android.app.AlertDialog;import android.content.*;import android.graphics.*;import android.graphics.drawable.ColorDrawable;import android.os.*;import android.text.*;import android.view.*;import android.widget.*;import java.util.*;import java.util.concurrent.*;
/** Translucent channel menu over the independent decoder, backed by the real CHL catalogue. */
public class ReceiverLibraryActivity extends HubActivity {
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();private ReceiverCatalog catalog;private List<ReceiverCatalog.Channel> shown=Collections.emptyList();private List<ReceiverCatalog.Sat> satellites=Collections.emptyList();private List<ReceiverCatalog.Group> groups=Collections.emptyList();private Set<Long> favorites=Collections.emptySet();private Set<String> ambiguousPhysical=Collections.emptySet();private Map<String,SatelliteGuideRepository.Event> programmes=Collections.emptyMap();private Map<Long,Integer> satelliteNumbers=Collections.emptyMap();private String lastLiveTitle="";
 private EditText query;private ListView list;private TextView status,liveTitle,rf,signalDetail,focusedProgramme;private ProgressBar strength,quality;private Button satelliteButton,favoritesButton,previous,next;private boolean destroyed,loaded,favoritesOnly,alphabetical,initialPopup,encryptedOnly,wideList;private long satellite=-1,activeId=-1,group=-1;private int type=-1,offset,generation,total,nextListSelection=-1;private long nextListFocusId=-1;private DirectReceiverSignal reader;
 private final Runnable filter=()->search(true);private final Runnable tick=new Runnable(){public void run(){if(!destroyed){refreshSignal();main.postDelayed(this,1000);}}};
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));ChannelScope saved=ChannelScope.read(this);type=getIntent().getIntExtra("type",saved.type);satellite=getIntent().getLongExtra("satellite",saved.satellite);favoritesOnly=getIntent().getBooleanExtra("favorites",saved.favorites);encryptedOnly=getIntent().getBooleanExtra("encrypted_only",false);group=saved.group;alphabetical=saved.alphabetical;catalog=ReceiverCatalog.get(this);wideList=prefs().getBoolean("channel_list_wide_mode",false);FrameLayout backdrop=new FrameLayout(this);backdrop.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);LinearLayout root=col();root.setPadding(dp(15),dp(8),dp(15),dp(8));root.setBackground(ReceiverPanelStyle.panel(this));FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(Math.min(dp(wideList?960:620),(int)(getResources().getDisplayMetrics().widthPixels*(wideList?.86f:.58f))),-1,Gravity.LEFT);panel.setMargins(dp(14),dp(10),0,dp(10));backdrop.addView(root,panel);
  LinearLayout header=row();satelliteButton=tool(header,tx("‹  كل الأقمار  ›","‹  All satellites  ›"),this::satellites);satelliteButton.setTextColor(ReceiverPanelStyle.GOLD);satelliteButton.setTextSize(font(20));root.addView(header);
  query=field(root,tx("البحث عن قناة","Search channels"),"");query.setTextSize(font(13));query.setPadding(dp(8),dp(3),dp(8),dp(3));query.setContentDescription("channel-search");query.setBackgroundColor(0x44384b55);query.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(30)));LinearLayout tools=row();favoritesButton=tool(tools,tx("المفضلة","Favourites"),this::groups);favoritesButton.setOnLongClickListener(v->{manageGroups();return true;});tool(tools,tx("النوع","Type"),this::types);tool(tools,"A–Z",()->{alphabetical=!alphabetical;search(true);});tool(tools,wideList?tx("Aurum ✓","Aurum ✓"):tx("DVB ✓","DVB ✓"),this::toggleListMode);tool(tools,tx("بحث / استيراد","Import / tools"),()->startActivity(new Intent(this,ReceiverToolsActivity.class)));Button software=tool(tools,prefs().getBoolean("dvb_422_compat_all",false)?"4:2:2 ✓":"4:2:2",()->{boolean enabled=!prefs().getBoolean("dvb_422_compat_all",false);prefs().edit().putBoolean("dvb_422_compat_all",enabled).apply();recreate();});root.addView(tools);
  // OEM has two distinct channel-list controllers. Give each actual actions,
  // not just a wider window: Aurum mode exposes TP/search; DVB mode focuses EPG.
  LinearLayout modeTools=row();
  if(wideList){
   tool(modeTools,tx("تردد القناة","Selected TP"),this::focusedTransponder);
   tool(modeTools,tx("بحث القنوات","Find channels"),this::focusSearch);
  }else{
   tool(modeTools,tx("دليل القناة المحددة","Selected EPG"),this::focusedGuide);
   tool(modeTools,tx("معلومات القناة","Channel details"),this::focusedDetails);
  }
  root.addView(modeTools);
  status=text(tx("قراءة القنوات…","Loading channels…"),10,false);status.setTextColor(MUTED);root.addView(status);list=new ListView(this);list.setDividerHeight(0);list.setSelector(android.R.color.transparent);list.setOnItemClickListener((p,v,index,id)->play(shown.get(index)));
  list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
    updateFocusedProgramme(position);
   }
   @Override public void onNothingSelected(AdapterView<?> parent){}
  });list.setOnItemLongClickListener((p,v,index,id)->{details(shown.get(index));return true;});root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout live=col();liveTitle=text("",12,true);liveTitle.setTextColor(ReceiverPanelStyle.CYAN);live.addView(liveTitle);
  focusedProgramme=text("",11,false);
  focusedProgramme.setTextColor(0xfff3d77d);live.addView(focusedProgramme);LinearLayout meters=row();strength=meter(0xff32c9e7);quality=meter(0xffe835c9);meters.addView(strength,new LinearLayout.LayoutParams(0,dp(6),1));View gap=new View(this);meters.addView(gap,new LinearLayout.LayoutParams(dp(22),1));meters.addView(quality,new LinearLayout.LayoutParams(0,dp(6),1));live.addView(meters);signalDetail=text("",11,false);signalDetail.setTextDirection(View.TEXT_DIRECTION_LTR);signalDetail.setTextColor(WHITE);live.addView(signalDetail);rf=text("",11,false);rf.setSingleLine(true);rf.setEllipsize(TextUtils.TruncateAt.END);rf.setTextColor(0xff5ef39b);live.addView(rf);root.addView(live);
  LinearLayout pager=row();previous=tool(pager,"‹",()->{offset=Math.max(0,offset-120);search(false);});tool(pager,tx("★ إضافة","★ Favourite"),()->{int p=list.getSelectedItemPosition();if(p>=0&&p<shown.size())favorite(shown.get(p));});next=tool(pager,"›",()->{offset+=120;search(false);});tool(pager,"INFO",()->{int p=list.getSelectedItemPosition();if(p>=0&&p<shown.size())details(shown.get(p));});root.addView(pager);TextView hint=text(tx("◄ ►  ±10 قنوات   ·   PAGE↓  شفرة BISS   ·   PAGE↑  المشفرة\nأحمر: مفضلة · أخضر: نوع · أصفر: A–Z · أزرق: إشارة",
  "◄ ►  ±10 channels   ·   PAGE↓  BISS   ·   PAGE↑  Encrypted\nRED: Favourites · GREEN: Type · YELLOW: A–Z · BLUE: Signal"),10,false);hint.setTextColor(ReceiverPanelStyle.GOLD);root.addView(hint);String initial=getIntent().getStringExtra("query");query.setText(initial==null?saved.query:initial);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);backdrop.setFocusableInTouchMode(true);backdrop.requestFocus();setContentView(backdrop);query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){main.removeCallbacks(filter);main.postDelayed(filter,160);}public void afterTextChanged(Editable e){}});
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
 @Override protected void onResume(){super.onResume();reader=ReceiverInfoRepository.get(this).acquireSignal();main.post(tick);int token=++generation;ChannelScope browse=scope();io.execute(()->{try{catalog.ensure();List<ReceiverCatalog.Sat> sats=catalog.satellites();List<ReceiverCatalog.Group> fav=catalog.groups();int count=catalog.count();Map<Long,Integer> numbers=catalog.satelliteNumbers();Set<String> collisions=catalog.ambiguousPhysicalKeys();ReceiverCatalog.Channel active=catalog.channel(prefs().getLong("own_dvb_channel",-1));ChannelScope checked=catalog.validScope(browse);int activePage=active==null?0:catalog.pageFor(active,checked);main.post(()->{if(destroyed||token!=generation)return;satellites=sats;groups=fav;satelliteNumbers=numbers;ambiguousPhysical=collisions;total=count;satellite=checked.satellite;group=checked.group;satelliteButton.setText(tx("‹  كل الأقمار  ›","‹  All satellites  ›"));favoritesButton.setText(favoritesOnly?tx("مفضلتي في IRAQhub","My IRAQhub favourites"):tx("المفضلة","Favourites"));boolean anchor=!loaded&&active!=null&&(type<0||active.tv==type)&&SatelliteState.read(this).channelId.equals("DVB:"+active.physical());
long restoreId=prefs().getLong("channel_list_restore_focus_id",-1);
int restorePage=prefs().getInt("channel_list_restore_focus_page",-1);
loaded=true;for(ReceiverCatalog.Sat sat:sats)if(sat.id==satellite)satelliteButton.setText("‹  "+sat.name+"  ›");if(group>=0)for(ReceiverCatalog.Group g:fav)if(g.id==group)favoritesButton.setText(g.name);if(restoreId>=0&&restorePage>=0){
   offset=restorePage;nextListFocusId=restoreId;
   prefs().edit().remove("channel_list_restore_focus_id")
    .remove("channel_list_restore_focus_page").apply();
   search(false);
  }else if(anchor){offset=activePage;search(false);}else search(true);if(!initialPopup){initialPopup=true;if(getIntent().getBooleanExtra("satellite_popup",false))satellites();else if(getIntent().getBooleanExtra("favorites",false))groups();}});}catch(Exception unavailable){main.post(()->{if(!destroyed)status.setText(tx("تعذر الفهرس؛ استورد CHL","Import CHL to restore the catalogue"));});}});}
 @Override protected void onPause(){main.removeCallbacks(tick);if(reader!=null){ReceiverInfoRepository.get(this).releaseSignal(reader);reader=null;}super.onPause();}
 private ChannelScope scope(){return new ChannelScope(query.getText().toString(),satellite,type,favoritesOnly,alphabetical,group);}
 private void pageChannels(int direction){pageChannels(direction,10);}
 private void pageChannels(int direction,int jump){
  if(shown.isEmpty())return;
  // Left/right skips ten channels within the actual list, across 120-item pages.
  int selected=Math.max(0,list.getSelectedItemPosition());
  int absolute=Math.max(0,offset+selected+direction*jump);
  if(absolute>=offset+shown.size()&&shown.size()<120)
   absolute=offset+shown.size()-1;
  int currentPage=(absolute/120)*120;
  if(currentPage==offset){
   list.requestFocus();list.setSelection(Math.min(shown.size()-1,absolute-offset));
  }else{
   offset=currentPage;nextListSelection=absolute-currentPage;search(false);
  }
 }
 private void search(boolean reset){if(!loaded)return;if(reset)offset=0;scope().save(this);String q=query.getText().toString();long sat=satellite,favGroup=group;int kind=type,page=offset,token=++generation;boolean fav=favoritesOnly,alpha=alphabetical;io.execute(()->{try{List<ReceiverCatalog.Channel> rows=catalog.channels(q,sat,kind,fav,alpha,page,favGroup,encryptedOnly);Set<Long> selected=catalog.favorites();Set<String> physical=new HashSet<>();for(ReceiverCatalog.Channel row:rows)physical.add("DVB:"+row.physical());long epgNow=System.currentTimeMillis();
 Map<String,SatelliteGuideRepository.Event> events=
  new SatelliteGuideRepository(this).currentChannels(physical,epgNow);
 // Persist only positively timed, physically identified programmes for
 // reliable offline Infobar/channel-list fallback on the next launch.
 try{
  UnifiedEpgIndex local=UnifiedEpgIndex.get(this);
  local.record(events,epgNow);
  for(String identity:physical)if(!events.containsKey(identity)){
   SatelliteGuideRepository.Event cached=local.current(identity,epgNow);
   if(cached!=null)events.put(identity,cached);
  }
 }catch(Exception unavailable){/* Original guide remains authoritative. */}
 long current=prefs().getLong("own_dvb_channel",-1);if(!SatelliteState.read(this).channelId.startsWith("DVB:"))current=catalog.uniqueChannel(SatelliteState.read(this).channelName);long playing=current;main.post(()->{if(destroyed||token!=generation)return;shown=rows;favorites=selected;programmes=events;activeId=playing;status.setText((sat<0?tx("كل الأقمار","All satellites"):satelliteButton.getText())+
   (encryptedOnly?tx(" · مشفرة فقط"," · encrypted only"):"")+
   tx("  ·  من ","  ·  positions ")+(rows.isEmpty()?"0":(page+1)+"–"+(page+rows.size()))+(alphabetical?"  A–Z":""));previous.setEnabled(page>0);next.setEnabled(rows.size()==120);list.setAdapter(new Rows());if(!rows.isEmpty())updateFocusedProgramme(
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
  if(prefs().getBoolean("dvb_422_compat_all",false)&&ch.ca>=0&&ch.tv==0)playSoftware(ch);
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
    .setPositiveButton(tx("تجربة","Try"),(dialog,which)->launchSoftware(ch))
    .setNegativeButton(tx("إلغاء","Cancel"),null).show();
   return;
  }
  launchSoftware(ch);
 }
 private void launchSoftware(ReceiverCatalog.Channel ch){
  new ChannelScope("",satellite,ch.tv,favoritesOnly,alphabetical,group).save(this);
  startActivity(new Intent(this,SoftwareVideoActivity.class).putExtra("dvb_channel",ch.id));
  finish();
 }
 private void details(ReceiverCatalog.Channel ch){
  String[] items=new String[]{
    tx("تشغيل القناة","Play with normal decoder"),
    tx("تشغيل برمجي 4:2:2 · LibVLC","Software 4:2:2 · LibVLC"),
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
 private void refreshSignal(){SatelliteState s=SatelliteState.read(this);if(reader!=null)reader.refresh(s);ReceiverInfoRepository.Info info=ReceiverInfoRepository.get(this).info(s);liveTitle.setText(s.channelName);String liveKey=s.channelId+"|"+s.title+"|"+(System.currentTimeMillis()/30000);if(!lastLiveTitle.equals(liveKey)){lastLiveTitle=liveKey;list.invalidateViews();}rf.setText(info.broadcast);strength.setProgress(percent(info.strength),true);quality.setProgress(percent(info.quality),true);strength.setVisibility(info.strength.isEmpty()?View.INVISIBLE:View.VISIBLE);quality.setVisibility(info.quality.isEmpty()?View.INVISIBLE:View.VISIBLE);signalDetail.setText("S: "+(info.strength.isEmpty()?"—":info.strength)+"     Q: "+(info.quality.isEmpty()?"—":info.quality)+"     "+info.signalDetail());}
 private static int percent(String s){try{return (int)Float.parseFloat(s.replace("%",""));}catch(Exception no){return 0;}}
 private final class Rows extends BaseAdapter {
 private final SatelliteState active=ReceiverInfoRepository.get(ReceiverLibraryActivity.this).current();
 public boolean hasStableIds(){return true;}
 public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return shown.get(p).id;}public View getView(int position,View old,ViewGroup parent){ReceiverCatalog.Channel ch=shown.get(position);String identity=ch.physical();LinearLayout row=row();row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);row.setPadding(dp(5),dp(3),dp(8),dp(3));row.setBackground(ReceiverPanelStyle.row(ReceiverLibraryActivity.this));int screen=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);row.setLayoutParams(new AbsListView.LayoutParams(-1,dp(Math.max(Math.round(font(17)+font(11)+12),Math.min(72,list.getHeight()>0?(int)(list.getHeight()/getResources().getDisplayMetrics().density/8):(screen-260)/8)))));TextView number=text(Integer.toString(satelliteNumbers.getOrDefault(ch.id,position+offset+1)),16,false);number.setTextColor(ReceiverPanelStyle.GOLD);row.addView(number,new LinearLayout.LayoutParams(dp(38),-2));ImageView logo=new ImageView(ReceiverLibraryActivity.this);logo.setTag(identity);logo.setImageResource(R.drawable.channel_placeholder);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout logoStack=col();logoStack.setGravity(Gravity.CENTER);logoStack.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(31)));ImageView providerMark=new ImageView(ReceiverLibraryActivity.this);providerMark.setScaleType(ImageView.ScaleType.FIT_CENTER);providerMark.setVisibility(View.GONE);logoStack.addView(providerMark,new LinearLayout.LayoutParams(dp(23),dp(10)));row.addView(logoStack,new LinearLayout.LayoutParams(dp(52),-2));LinearLayout info=col();info.setPadding(dp(10),0,dp(6),0);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));TextView name=text((favorites.contains(ch.id)?"★ ":"")+ch.name,17,false);name.setTextColor(0xfff8f6e9);name.setGravity(Gravity.LEFT);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);info.addView(name);boolean current=ch.id==activeId&&active.channelName.equalsIgnoreCase(ch.name);SatelliteGuideRepository.Event cached=programmes.get("DVB:"+identity);String title=current&&!active.title.isEmpty()?active.title:cached!=null&&cached.startMs<=System.currentTimeMillis()&&cached.endMs>System.currentTimeMillis()?cached.title:"";TextView epg=text(title.isEmpty()?tx("لا توجد بيانات برنامج","No programme information"):title,11,false);epg.setTextColor(current?0xff75f5ad:MUTED);epg.setSingleLine(true);epg.setEllipsize(TextUtils.TruncateAt.END);info.addView(epg);if(current){String badge=ResolutionBadge.label(ReceiverLibraryActivity.this,active);if(!badge.isEmpty()){TextView mark=text(badge,10,true);mark.setGravity(Gravity.CENTER);mark.setBackgroundResource(ResolutionBadge.asset(badge));mark.setTextColor(ResolutionBadge.color(badge));row.addView(mark,new LinearLayout.LayoutParams(dp(51),dp(21)));}}if(ambiguousPhysical.contains(identity)){
  logo.setContentDescription(tx("هوية DVB مكررة · الشعار محجوب لتجنب الخطأ",
    "Ambiguous DVB identity · logo omitted to avoid wrong association"));
 }else LogoRepository.get(ReceiverLibraryActivity.this).request(ch.name,"DVB:"+identity,new LogoRepository.Listener(){public void channel(Bitmap b){if(!destroyed&&identity.equals(logo.getTag())&&b!=null)logo.setImageBitmap(b);}public void provider(String n,Bitmap b){if(!destroyed&&identity.equals(logo.getTag())){providerMark.setImageBitmap(b);providerMark.setContentDescription(n);providerMark.setVisibility(b==null?View.GONE:View.VISIBLE);}}});return row;}}
 @Override public boolean dispatchKeyEvent(KeyEvent e){int k=e.getKeyCode();
  if(k==KeyEvent.KEYCODE_GUIDE||k==KeyEvent.KEYCODE_SEARCH||k==KeyEvent.KEYCODE_MENU){
   if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
    if(k==KeyEvent.KEYCODE_GUIDE)focusedGuide();
    else if(k==KeyEvent.KEYCODE_SEARCH)focusSearch();
    else focusedDetails();
   }
   return true;
  }if(k==KeyEvent.KEYCODE_F&&query.hasFocus())return super.dispatchKeyEvent(e);if(k==KeyEvent.KEYCODE_PAGE_DOWN){
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
  if(k==17||k==237||k==34||k==440||k==KeyEvent.KEYCODE_TV_RADIO_SERVICE||k==170||k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_CHANNEL_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){if(k==17||k==237)satellites();else if(k==34||k==440)groups();else if(k==KeyEvent.KEYCODE_TV_RADIO_SERVICE||k==170){type=type==1?0:1;search(true);}else pageChannels(k==KeyEvent.KEYCODE_CHANNEL_UP?1:-1,1);}return true;}if(k==KeyEvent.KEYCODE_PROG_RED||k==KeyEvent.KEYCODE_PROG_GREEN||k==KeyEvent.KEYCODE_PROG_YELLOW||k==KeyEvent.KEYCODE_PROG_BLUE||k==KeyEvent.KEYCODE_INFO){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){if(k==KeyEvent.KEYCODE_PROG_RED)groups();else if(k==KeyEvent.KEYCODE_PROG_GREEN)types();else if(k==KeyEvent.KEYCODE_PROG_YELLOW){alphabetical=!alphabetical;search(true);}else if(k==KeyEvent.KEYCODE_PROG_BLUE)startActivity(new Intent(this,SignalInformationActivity.class));else{int p=list.getSelectedItemPosition();if(p>=0&&p<shown.size())details(shown.get(p));}}return true;}return super.dispatchKeyEvent(e);}
 @Override protected void onDestroy(){destroyed=true;generation++;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
