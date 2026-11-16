package com.smarthub.plus;
import android.app.AlertDialog;import android.content.*;import android.graphics.*;import android.graphics.drawable.ColorDrawable;import android.os.*;import android.provider.Settings;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.concurrent.*;

/** Own DVB viewing window: the firmware decoder renders video under this transparent surface. */
public final class DvbPlaybackActivity extends HubActivity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());private ReceiverPlayback player;private LocalInfoBarController localInfo;private volatile ReceiverCatalog.Channel channel,selection;private TextView message;private boolean closed,resumed,playing;private int audioIndex;private IPAudioController internetAudio;
 private ReceiverPlaybackOverlay remote;
 private ReceiverCatalog.Channel tuneTarget;
 private boolean targetFailed,targetNoSignal;
 private final ChannelNumberInput numbers=new ChannelNumberInput();
 private final Runnable autoZap=this::commitNumber;private final BlueKeyGesture blue=new BlueKeyGesture(new BlueKeyGesture.Clock(){public long now(){return SystemClock.elapsedRealtime();}public void post(Runnable r,long delay){main.postDelayed(r,delay);}public void cancel(Runnable r){main.removeCallbacks(r);}},new BlueKeyGesture.Listener(){public void single(){if(localInfo!=null)localInfo.showProgrammeDetails();}public void signal(){DvbPlaybackActivity.this.signal();}public void assistant(){companion();}});
 @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));getWindow().setFormat(PixelFormat.TRANSLUCENT);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);message=text(tx("تحضير البث الفضائي…","Opening satellite TV…"),17,true);message.setGravity(Gravity.CENTER);message.setPadding(dp(24),dp(12),dp(24),dp(12));android.graphics.drawable.GradientDrawable statusCard=new android.graphics.drawable.GradientDrawable();statusCard.setColor(0xee151f2c);statusCard.setCornerRadius(dp(10));statusCard.setStroke(dp(1),0xffc99652);message.setBackground(statusCard);FrameLayout.LayoutParams statusPosition=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);statusPosition.setMargins(dp(40),0,dp(40),0);root.addView(message,statusPosition);
 setContentView(root);localInfo=new LocalInfoBarController(this,root);remote=new ReceiverPlaybackOverlay(this,root);player=new ReceiverPlayback(this,new ReceiverPlayback.Listener(){public void accepted(ReceiverCatalog.Channel c){if(channel!=null&&channel.id!=c.id)prefs().edit().putLong("own_dvb_previous",channel.id).apply();playing=true;channel=c;
 io.execute(()->{
  int number=ReceiverCatalog.get(DvbPlaybackActivity.this).satelliteNumber(c);
  main.post(()->{if(!closed&&channel!=null&&channel.id==c.id){
   FrontPanelBridge.publish(DvbPlaybackActivity.this,c,number);
   prefs().edit().putInt("own_dvb_sat_number",number)
    .putString("own_dvb_sat_number_channel","DVB:"+c.physical()).apply();
  }});
 });SatelliteState old=SatelliteState.read(DvbPlaybackActivity.this),s=new SatelliteState();if(old.channelId.equals("DVB:"+c.physical()))s=old.copy();s.channelName=c.name;s.channelId="DVB:"+c.physical();s.source="DVB · IRAQhub";s.observedAt=System.currentTimeMillis();s.save(DvbPlaybackActivity.this);prefs().edit().putString("own_dvb_physical",c.physical())
  .putString("own_dvb_rf",c.rf()).putBoolean("own_dvb_fta",c.ca==0)
  .putString("own_ca_channel","DVB:"+c.physical())
  .putInt("own_ca_flag",c.ca)
  .putString("own_ca_systems",ReceiverTechnicalBadges.caIds(c.data.optJSONArray("CaSystemIdList")))
  .apply();audioIndex=prefs().getInt("dvb_audio_index:"+c.physical(),0);publishAudio(audioIndex);if(internetAudio!=null)internetAudio.apply(c.physical());prefs().edit().putBoolean("dvb_tuning",false).putBoolean("dvb_player_visible",resumed).apply();ReceiverInfoRepository.get(DvbPlaybackActivity.this).invalidateSignal();message.setVisibility(View.GONE);tuneTarget=null;targetFailed=false;targetNoSignal=false;if(localInfo!=null)localInfo.showTuned(resumed);}public void error(String code){
    if(code!=null&&code.startsWith("DVB_AUDIO_")){
      toast(tx("لم يتأكد تغيير الصوت: ","Audio switch not confirmed: ")+code);
      return;
    }
    prefs().edit().putBoolean("dvb_tuning",false).apply();
    boolean noLock="DVB_NOT_LOCKED".equals(code);
    if(tuneTarget!=null){
     targetFailed=true;targetNoSignal=noLock;
     if(localInfo!=null&&resumed)localInfo.showZapTarget(tuneTarget,noLock);
    }
    message.setVisibility(noLock?View.GONE:View.VISIBLE);
    if(!noLock)message.setText(errorText(code));
   }public void pip(boolean accepted){toast(accepted?tx("قبِل الجهاز طلب PIP؛ تحقّق من النافذة المصغرة","Receiver accepted PIP; check the small video window"):tx("PIP غير متاح لهذه القناة أو الدقة","PIP is unavailable for this channel or resolution"));}public void audio(int selected){
    audioIndex=selected;publishAudio(selected);
    if(internetAudio!=null)internetAudio.stop();
    player.nativeAudio(true);
    toast(tx("تأكد تبديل المسار الصوتي من الجهاز","Receiver confirmed audio track change"));
   }});internetAudio=new IPAudioController(this,enabled->player.nativeAudio(enabled));long requested=getIntent().getLongExtra("channel",-1);io.execute(()->{ReceiverCatalog catalog=ReceiverCatalog.get(this);catalog.ensure();long id=requested>=0?requested:(prefs().getBoolean("own_dvb_startup_enabled",false)?prefs().getLong("own_dvb_startup",-1):prefs().getLong("own_dvb_channel",-1));ReceiverCatalog.Channel picked=catalog.channel(id);if(picked==null){String name=SatelliteState.read(this).channelName;picked=catalog.channel(catalog.uniqueChannel(name));}ReceiverCatalog.Channel target=picked;main.post(()->{if(closed)return;if(target==null)message.setText(tx("اختر قناة من قائمة IRAQhub بعد استيراد ملف CHL الكامل","Import a full CHL file and choose a channel in IRAQhub"));else{selection=target;playTarget(target);}});});}
 private String errorText(String code){switch(code){case "DVB_TUNING_PROFILE_REQUIRED":return tx("احفظ إعداد LNB لهذا القمر من قائمة IRAQhub ← أدوات الرسيفر ← ضبط الموالف.","Save this satellite’s LNB profile in IRAQhub → Receiver tools → Tuner settings.");case "DVB_RADIO_AUDIO_REQUIRED":return tx("ملف القناة لا يتضمن مسار راديو صالحاً.","Channel metadata has no valid radio audio track.");case "DVB_METADATA_REQUIRED":return tx("استورد ملف CHL الكامل الذي يتضمن بيانات الفيديو والصوت.","Import a full CHL file with video and audio metadata.");case "DVB_VIDEO_STALLED":return tx("إشارة القمر موجودة لكن عداد إطارات الفيديو توقف. افحص توافق 4:2:2 أو بث TS من تشخيص الجهاز؛ لم يثبت سبب العطل.","Satellite signal is locked but video frames stalled. Check 4:2:2 compatibility or TS diagnostics; the cause is not confirmed.");case "DVB_CA_VIDEO_UNCONFIRMED":return tx("توقف تقدم إطارات الفيديو. افحص حالة التخويل وBISS وبث TS وتوافق 4:2:2؛ هذا لا يثبت أن الشفرة خاطئة.","Video frames stopped advancing. Check CA/BISS status, TS and 4:2:2 support; this does not prove the key is wrong.");case "DVB_CA_METADATA_REQUIRED":return tx("بيانات CA أو PMT غير مكتملة؛ افتح خدمات القناة الأصلية.","CA / PMT metadata missing; open firmware channel services.");case "DVB_ENCRYPTED_NOT_VERIFIED":return tx("التشغيل المستقل للقنوات المشفرة لم يُتحقق منه بعد.","Independent encrypted-channel playback is not verified yet.");case "DVB_NOT_LOCKED":return tx("الموالف غير مقفل على إشارة؛ تحقق من التردد وإعدادات الطبق.","No tuner lock; check the transponder and dish configuration.");default:return tx("تعذر تشغيل البث أو مسار الصوت: ","Playback or audio failed: ")+code;}}
 @Override protected void onResume(){
  super.onResume();
  resumed=true;
  prefs().edit().putBoolean("dvb_player_visible",playing).apply();
  if(tuneTarget!=null&&localInfo!=null){
   localInfo.showZapTarget(tuneTarget,targetFailed&&targetNoSignal);
  }else if(playing&&localInfo!=null){
   localInfo.resumeSignalMonitoring();
  }
  if(playing&&channel!=null&&internetAudio!=null){
   internetAudio.apply(channel.physical());
   ReceiverCatalog.Channel active=channel;
   boolean restore=prefs().getBoolean("own_dvb_restore_after_oem",false);
   prefs().edit().remove("own_dvb_restore_after_oem").apply();
   io.execute(()->{
    try{
     ReceiverSignalReading frame=DvbTuner.read();
     if(restore||frame.locked!=1||frame.tp!=prefs().getInt("own_dvb_runtime_tp",-1)){
      main.post(()->{if(!closed&&resumed&&channel==active&&tuneTarget==null)playTarget(active);});
     }
    }catch(Exception unavailable){}
   });
  }
 }
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);long id=intent.getLongExtra("channel",channel==null?-1:channel.id);if(channel==null||id!=channel.id)playing=false;io.execute(()->{ReceiverCatalog.Channel selected=ReceiverCatalog.get(this).channel(id);if(selected!=null)selection=selected;main.post(()->{if(!closed&&selected!=null){if(channel!=null&&channel.id==selected.id&&playing){if(internetAudio!=null)internetAudio.apply(selected.physical());}else{playTarget(selected);}}});});}
 @Override protected void onPause(){resumed=false;blue.cancel();main.removeCallbacks(autoZap);numbers.clear();if(remote!=null)remote.digits("");if(localInfo!=null)localInfo.dismiss();prefs().edit().putBoolean("dvb_player_visible",false).apply();super.onPause();}
 private void publishAudio(int index){if(channel==null)return;org.json.JSONArray tracks=channel.audio();org.json.JSONObject selected=null;int valid=0;for(int n=0;n<Math.min(16,tracks.length());n++){org.json.JSONObject t=tracks.optJSONObject(n);if(t!=null&&DvbPlayRequest.audio(t.optString("Type"))>=0&&t.optInt("PID",8191)>0&&t.optInt("PID",8191)<8191){if(valid++==index){selected=t;break;}}}if(selected!=null)prefs().edit().putString("own_dvb_audio",selected.optString("Lang")+" · "+selected.optString("Type")).apply();}
 private void companion(){
  if(localInfo!=null)localInfo.hideForCompanion();
  SatelliteActivity.openPlaybackCompanion(this);
 }
 private void info(){if(localInfo!=null)localInfo.showDetails();}
 private void volume(int key){int level=prefs().getInt("dvb_volume",20);boolean mute=prefs().getBoolean("dvb_muted",false);if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN){level=Math.max(0,Math.min(100,level+(key==KeyEvent.KEYCODE_VOLUME_UP?5:-5)));mute=false;}else mute=!mute;prefs().edit().putInt("dvb_volume",level).putBoolean("dvb_muted",mute).apply();player.volumeChanged();if(internetAudio!=null)internetAudio.remoteVolume(level,mute);if(remote!=null)remote.volume(level,mute);}
 private void numberDigit(int digit){
  numbers.add(digit);
  if(remote!=null)remote.digits(numbers.display());
  main.removeCallbacks(autoZap);
  main.postDelayed(autoZap,2300);
 }
 private void commitNumber(){
  main.removeCallbacks(autoZap);
  int requested=numbers.number();
  numbers.clear();
  if(remote!=null)remote.digits("");
  if(requested<1)return;
  ReceiverCatalog.Channel active=selection==null?channel:selection;
  if(active==null)return;
  io.execute(()->{
   ReceiverCatalog.Channel target=ReceiverCatalog.get(this).channelAtNumber(active,requested);
   main.post(()->{
    if(closed||!resumed)return;
    if(target==null){
     toast(tx("رقم القناة غير موجود: ","Channel number unavailable: ")+requested);
     return;
    }
    selection=target;
    playTarget(target);
   });
  });
 }
 private void bissPopup(){
  ReceiverCatalog.Channel current=channel==null?selection:channel;
  if(current==null){toast(tx("اختر قناة أولاً","Select a channel first"));return;}
  BissKeyDialog.show(this,current);
 }
 private void recall(){long id=prefs().getLong("own_dvb_previous",-1);if(id>=0)startActivity(new Intent(this,DvbPlaybackActivity.class).putExtra("channel",id));}
 private void signal(){startActivity(new Intent(this,SignalInformationActivity.class));}
 private static final int AV_PANEL_REQUEST=9052;
 private void audio(){avPanel(false);}
 private void avPanel(boolean subtitles){
  if(channel==null||!playing||tuneTarget!=null)return;
  startActivityForResult(new Intent(this,ReceiverAvPanelActivity.class)
    .putExtra("show_subtitle",subtitles).putExtra("channel_id",channel.id)
    .putExtra("channel_physical",channel.physical()),AV_PANEL_REQUEST);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==AV_PANEL_REQUEST&&result==RESULT_OK&&data!=null&&player!=null){
   int index=data.getIntExtra("audio_index",-1);
   String expected=data.getStringExtra("channel_physical");
   if(data.getBooleanExtra("software_subtitles",false)){
    if(channel==null||!playing||tuneTarget!=null||!channel.physical().equals(expected)){
     toast(tx("تغيّرت القناة؛ أعد اختيار الترجمة","Channel changed; select subtitles again"));
     return;
    }
    long channelId=channel.id;
    playing=false;
    if(internetAudio!=null)internetAudio.stop();
    player.closeAfterRelease(()->{
     if(closed||isFinishing())return;
     startActivity(new Intent(this,SoftwareVideoActivity.class).putExtra("dvb_channel",channelId)
       .putExtra("decoder_engine","libvlc").putExtra("open_live_subtitles",true));
     finish();
    });
    return;
   }
   if(index>=0&&channel!=null&&channel.physical().equals(expected)){
    player.audio(index);
    toast(tx("أُرسل طلب تبديل الصوت؛ جارٍ انتظار تأكيد الجهاز",
      "Audio request sent; waiting for receiver acknowledgement"));
   }else if(index>=0){toast(tx("تغيّرت القناة؛ أعد اختيار الصوت","Channel changed; select audio again"));}
  }
 }
 private void pip(){if(channel==null)return;ReceiverCatalog.Channel active=channel;io.execute(()->{List<ReceiverCatalog.Channel> rows=ReceiverCatalog.get(this).sameTransponder(active);main.post(()->{if(closed)return;String[] labels=new String[rows.size()+7];labels[0]=tx("إيقاف PIP","Stop PIP");for(int n=0;n<rows.size();n++)labels[n+1]=rows.get(n).name;String[] controls={"↑","↓","←","→",tx("تكبير","Zoom in"),tx("تصغير","Zoom out")};for(int n=0;n<6;n++)labels[rows.size()+1+n]=controls[n];ReceiverPanelStyle.choose(this,tx("صورة داخل صورة · نفس التردد","PIP · same transponder"),labels,0,index->{if(index==0)player.stopPip();else if(index<=rows.size())player.pip(rows.get(index-1));else player.positionPip(index-rows.size()-1);});});});}
 private void playTarget(ReceiverCatalog.Channel target){
  tuneTarget=target;
  targetFailed=false;
  targetNoSignal=false;
  if(target.ca!=0)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
  message.setVisibility(View.GONE);
  playing=false;
  prefs().edit().putBoolean("dvb_player_visible",false).apply();
  if(localInfo!=null&&resumed)localInfo.showZapTarget(target,false);
  if(internetAudio!=null)internetAudio.stop();
  player.play(target);
 }
 private void next(int direction){next(direction,1);}
 private void next(int direction,int steps){
  io.execute(()->{
   ReceiverCatalog.Channel before=selection==null?channel:selection;
   if(before==null)return;
   ReceiverCatalog db=ReceiverCatalog.get(this);
   ChannelScope scope=db.validScope(ChannelScope.read(this));
   scope.save(this);
   ReceiverCatalog.Channel target=db.adjacent(before,direction,scope,steps);
   if(target!=null)selection=target;
   main.post(()->{
    if(closed||!resumed)return;
    if(target==null){toast(tx("لا توجد قنوات في القائمة المحددة","No channels in the selected list"));return;}
    playTarget(target);
   });
  });
 }


 @Override public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();
  int digit=key>=KeyEvent.KEYCODE_0&&key<=KeyEvent.KEYCODE_9?
      key-KeyEvent.KEYCODE_0:
      key>=KeyEvent.KEYCODE_NUMPAD_0&&key<=KeyEvent.KEYCODE_NUMPAD_9?
        key-KeyEvent.KEYCODE_NUMPAD_0:-1;
  if(digit>=0){
   if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)numberDigit(digit);
   return true;
  }
  if(numbers.hasInput()){
   if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
    if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)commitNumber();
    return true;
   }
   if(key==KeyEvent.KEYCODE_BACK){
    if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
     numbers.clear();main.removeCallbacks(autoZap);if(remote!=null)remote.digits("");
    }
    return true;
   }
  }
  if(key==KeyEvent.KEYCODE_BACK){
   boolean infoVisible=localInfo!=null&&localInfo.isVisible();
   if(infoVisible&&!localInfo.keepsVisible()){
    if(event.getAction()==KeyEvent.ACTION_UP){
     localInfo.dismiss();
    }
    return true;
   }
  }
  if(key==KeyEvent.KEYCODE_PAGE_DOWN||key==KeyEvent.KEYCODE_F3){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)bissPopup();return true;}if(key==KeyEvent.KEYCODE_PAGE_UP){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)
  startActivity(new Intent(this,ReceiverChannelsActivity.class).putExtra("encrypted_only",true).putExtra("type",0));return true;}
 // In full-screen video LEFT/RIGHT adjust audio; only the channel list pages channels.
 int directionAction=RemoteDirectionPolicy.action(key,false);
 if(directionAction!=RemoteDirectionPolicy.NONE){
  if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getRepeatCount()==0||event.getRepeatCount()%2==0))
   volume(directionAction==RemoteDirectionPolicy.VOLUME_UP?
     KeyEvent.KEYCODE_VOLUME_UP:KeyEvent.KEYCODE_VOLUME_DOWN);
  return true;
 }if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE||key==KeyEvent.KEYCODE_MUTE){if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getRepeatCount()==0||key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN))volume(key);return true;}if(key==174||key==KeyEvent.KEYCODE_LAST_CHANNEL||key==KeyEvent.KEYCODE_GUIDE||key==441||key==170||key==KeyEvent.KEYCODE_TV_RADIO_SERVICE){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(key==174||key==KeyEvent.KEYCODE_LAST_CHANNEL)recall();else if(key==KeyEvent.KEYCODE_GUIDE||key==441)startActivity(new Intent(this,SatelliteGuideActivity.class));else startActivity(new Intent(this,ReceiverChannelsActivity.class).putExtra("type",channel!=null&&channel.tv==1?0:1));}return true;}if(key==KeyEvent.KEYCODE_PROG_GREEN||key==KeyEvent.KEYCODE_PROG_YELLOW){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(key==KeyEvent.KEYCODE_PROG_GREEN)startActivity(new Intent(this,LiveTranslationActivity.class));else pip();}return true;}if(key==KeyEvent.KEYCODE_WINDOW){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)pip();return true;}if(key==KeyEvent.KEYCODE_CAPTIONS||key==439){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)avPanel(true);return true;}if(key==KeyEvent.KEYCODE_INFO||key==1202){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)info();return true;}if(key==34||key==440||key==17||key==237){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)startActivity(new Intent(this,ReceiverChannelsActivity.class).putExtra("favorites",key==34||key==440).putExtra("satellite_popup",key==17||key==237));return true;}if(key==KeyEvent.KEYCODE_PROG_BLUE){if(event.getAction()==KeyEvent.ACTION_DOWN)return blue.down();if(event.getAction()==KeyEvent.ACTION_UP)return blue.up();}if(key==KeyEvent.KEYCODE_PROG_RED||key==KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK||key==1200||key==222){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)audio();return true;}if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)next(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_DPAD_UP?1:-1);return true;}if(key==KeyEvent.KEYCODE_DPAD_CENTER){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)startActivity(new Intent(this,ReceiverChannelsActivity.class));return true;}return super.dispatchKeyEvent(event);}
 @Override protected void onDestroy(){closed=true;if(remote!=null)remote.close();main.removeCallbacks(autoZap);if(localInfo!=null)localInfo.close();main.removeCallbacksAndMessages(null);io.shutdownNow();if(internetAudio!=null)internetAudio.close();if(player!=null)player.close();if(player==null||player.owns())prefs().edit().putBoolean("dvb_player_visible",false).apply();super.onDestroy();}
}
