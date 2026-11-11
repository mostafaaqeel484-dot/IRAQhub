package com.smarthub.plus;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.ui.PlayerView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Media3 IPTV playback; channel identity and real XMLTV context feed the same companion. */
@androidx.media3.common.util.UnstableApi
public class Media3PlayerActivity extends HubActivity {
 private CueTranslationController live;private String trackLanguage="";private StyledSubtitles styled;private androidx.media3.ui.SubtitleView styledView;private ExoPlayer player;private PlayerView surface;private TextView title,status,caption,timing;private PlaylistRepository library;private PlaylistRepository.Channel channel;private List<PlaylistRepository.Channel> channels=new ArrayList<>();private List<SubtitleParser.Cue> cues=Collections.emptyList();private boolean closed,resume,assNative,resourcesHeld,softwareFallbackStarted;private long delay,lastPublish,lastTelemetryFlush;private final AtomicLong telemetryFrames=new AtomicLong(),telemetryOffsetUs=new AtomicLong(),telemetryDropped=new AtomicLong(),telemetryDropEvents=new AtomicLong(),telemetryLastDropElapsedMs=new AtomicLong();private String programmeKey="",profile="default";private final Handler handler=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();private int generation;
 private final Runnable clock=new Runnable(){public void run(){if(closed)return;if(player!=null){PlaybackClock.positionMs=player.getCurrentPosition();delay=SubtitleSyncRepository.offset(Media3PlayerActivity.this);CaptionClock.positionMs=player.getCurrentPosition();CaptionClock.playing=player.isPlaying();if(styledView!=null&&styled!=null)styledView.setCues(styled.at(player.getCurrentPosition()-delay));PlaybackClock.channelId=channel==null?"":channel.id;if(!cues.isEmpty()&&!assNative){String value=SubtitleParser.textAt(cues,player.getCurrentPosition()-delay);CaptionClock.text=value;if(!value.contentEquals(caption.getText()))caption.setText(value);caption.setVisibility(value.isEmpty()||styled!=null?View.INVISIBLE:View.VISIBLE);}if(System.currentTimeMillis()-lastPublish>1000){publish();}if(System.currentTimeMillis()-lastTelemetryFlush>=1000){flushDecoderTelemetry();}}handler.postDelayed(this,100);}};
 @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout root=col();root.setBackgroundColor(Color.BLACK);LinearLayout head=row();title=text("IRAQhub · Media3",20,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));small(head,tx("رجوع","Back"),this::finish);root.addView(head);status=text("",14,false);root.addView(status);FrameLayout frame=new FrameLayout(this);surface=new PlayerView(this);surface.setUseController(true);
 /* SurfaceView is PlayerView's default; retain platform YUV/HDR conversion. */surface.setKeepScreenOn(true);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1));caption=text("",28,true);caption.setGravity(Gravity.CENTER);caption.setVisibility(View.INVISIBLE);caption.setBackgroundColor(0xb0000000);caption.setShadowLayer(4,1,2,Color.BLACK);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);cp.setMargins(dp(25),0,dp(25),dp(35));frame.addView(caption,cp);styledView=new androidx.media3.ui.SubtitleView(this);frame.addView(styledView,new FrameLayout.LayoutParams(-1,-1));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));LinearLayout buttons=row();small(buttons,tx("السابقة","Previous"),()->change(-1));small(buttons,tx("تشغيل / إيقاف","Play / pause"),()->{if(player.isPlaying())player.pause();else player.play();});small(buttons,tx("التالية","Next"),()->change(1));small(buttons,tx("فك برمجي","Software decode"),this::openSoftwareDecoder);small(buttons,tx("ترجمة SRT / ASS","SRT / ASS subtitles"),this::subtitles);small(buttons,tx("المساعد","Companion"),()->{try{startForegroundService(new Intent(this,SatelliteCompanionService.class).setAction(SatelliteCompanionService.ACTION_OPEN));}catch(Exception e){toast(e.getMessage());}});root.addView(buttons);LinearLayout sync=row();small(sync,"−0.5 s",()->offset(-500));timing=text("",14,false);sync.addView(timing,new LinearLayout.LayoutParams(0,-2,1));small(sync,"+0.5 s",()->offset(500));small(sync,"A−",()->resize(-2));small(sync,"A+",()->resize(2));root.addView(sync);small(buttons,tx("مزامنة الترجمة","Subtitle sync"),()->new android.app.AlertDialog.Builder(this).setView(new SubtitleSyncControls(this)).setPositiveButton(tx("إغلاق","Close"),(d,w)->{}).show());setContentView(root);android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
  android.app.ActivityManager.MemoryInfo mem=new android.app.ActivityManager.MemoryInfo();
  if(am!=null)am.getMemoryInfo(mem);
  boolean lowMemory=(am!=null&&am.isLowRamDevice())||mem.totalMem<2L*1024*1024*1024;
  int targetBuffer=lowMemory?16*1024*1024:32*1024*1024;
  DefaultLoadControl loadControl=new DefaultLoadControl.Builder()
  .setBufferDurationsMs(lowMemory?8000:10000,lowMemory?24000:30000,1000,2500)
  .setTargetBufferBytes(targetBuffer)
  .setBackBuffer(0,false)
  .setPrioritizeTimeOverSizeThresholds(true)
  .build();
  DefaultRenderersFactory renderers=new DefaultRenderersFactory(this)
  .setEnableDecoderFallback(true)
  .setMediaCodecSelector(X5MDecoderSelector.forDevice(this))
  .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
  // Start a clean telemetry window for this playback session.
  prefs().edit().putLong("decoder_session_started_at",System.currentTimeMillis())
   .putLong("decoder_processed_frames",0).putLong("decoder_processing_offset_us",0)
   .putLong("decoder_dropped_frames",0).putLong("decoder_dropped_frame_events",0)
   .putLong("decoder_first_frame_at",0).putLong("decoder_first_frame_render_ms",-1)
   .remove("decoder_last_error").putLong("decoder_last_error_at",0)
   .putString("decoder_422_verification","SESSION_STARTED").apply();
  telemetryFrames.set(0);telemetryOffsetUs.set(0);telemetryDropped.set(0);telemetryDropEvents.set(0);telemetryLastDropElapsedMs.set(0);
  player=new ExoPlayer.Builder(this,renderers).setLoadControl(loadControl).build();surface.setPlayer(player);
  player.addAnalyticsListener(new AnalyticsListener(){
   @Override public void onVideoInputFormatChanged(AnalyticsListener.EventTime eventTime,Format format,androidx.media3.exoplayer.DecoderReuseEvaluation evaluation){
    String codecs=format.codecs==null?"":format.codecs;boolean profile422=VideoDecoderCompatibility.isKnown422CodecString(codecs);
    prefs().edit().putString("decoder_input_mime",format.sampleMimeType==null?"":format.sampleMimeType).putString("decoder_input_codecs",codecs).putInt("decoder_input_width",format.width).putInt("decoder_input_height",format.height).putBoolean("decoder_input_profile422",profile422).putString("decoder_422_verification",profile422?"PROFILE_DETECTED_NOT_YET_RENDERED":"NO_422_PROFILE_DETECTED").apply();
   }
   @Override public void onVideoDecoderInitialized(AnalyticsListener.EventTime eventTime,String decoderName,long initializedTimestampMs,long initializationDurationMs){
    prefs().edit().putString("decoder_name",decoderName==null?"Unknown":decoderName).putString("decoder_kind",VideoDecoderCompatibility.decoderKind(decoderName)).putLong("decoder_init_ms",Math.max(0,initializationDurationMs)).putLong("decoder_initialized_at",System.currentTimeMillis()).putString("decoder_422_verification",prefs().getBoolean("decoder_input_profile422",false)?"DECODER_INITIALIZED_PROFILE_UNVERIFIED":"DECODER_INITIALIZED").apply();
   }
   @Override public void onRenderedFirstFrame(AnalyticsListener.EventTime eventTime,Object output,long renderTimeMs){
    SharedPreferences p=prefs();boolean profile422=p.getBoolean("decoder_input_profile422",false);SharedPreferences.Editor edit=p.edit().putLong("decoder_first_frame_at",System.currentTimeMillis()).putLong("decoder_first_frame_render_ms",Math.max(0,renderTimeMs));if(profile422)edit.putString("decoder_422_verification","FIRST_FRAME_RENDERED_PROFILE_NOT_BITSTREAM_VERIFIED");edit.apply();
   }
   @Override public void onVideoFrameProcessingOffset(AnalyticsListener.EventTime eventTime,long totalProcessingOffsetUs,int frameCount){
    telemetryFrames.addAndGet(Math.max(0,frameCount));telemetryOffsetUs.addAndGet(totalProcessingOffsetUs);
   }
   @Override public void onDroppedVideoFrames(AnalyticsListener.EventTime eventTime,int droppedFrames,long elapsedMs){
    if(droppedFrames<=0)return;telemetryDropped.addAndGet(droppedFrames);telemetryDropEvents.incrementAndGet();telemetryLastDropElapsedMs.set(Math.max(0,elapsedMs));
   }
   @Override public void onVideoCodecError(AnalyticsListener.EventTime eventTime,Exception error){
    String message=error==null?"Unknown codec error":error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());if(message.length()>400)message=message.substring(0,400);prefs().edit().putString("decoder_last_error",message).putLong("decoder_last_error_at",System.currentTimeMillis()).putString("decoder_422_verification",prefs().getBoolean("decoder_input_profile422",false)?"422_PROFILE_CODEC_ERROR":"CODEC_ERROR").apply();
   }
  });
  live=new CueTranslationController(this,c->{if(styled==null&&cues.isEmpty())styledView.setCues(c);});player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage("ar").setSelectUndeterminedTextLanguage(true).build());player.addListener(new Player.Listener(){@Override public void onVideoSizeChanged(VideoSize size){prefs().edit().putString("decoder_channel",channel==null?"":channel.id).putInt("decoder_width",size.width).putInt("decoder_height",size.height).apply();}@Override public void onCues(androidx.media3.common.text.CueGroup group){boolean translate=prefs().getBoolean("live_translate",false);if(surface.getSubtitleView()!=null)surface.getSubtitleView().setVisibility(translate?View.INVISIBLE:View.VISIBLE);if(translate&&styled==null&&cues.isEmpty())live.accept(group.cues,trackLanguage);else live.clear();}@Override public void onTracksChanged(Tracks tracks){StringBuilder summary=new StringBuilder();for(Tracks.Group g:tracks.getGroups())for(int n=0;n<g.length;n++){Format f=g.getTrackFormat(n);if(g.getType()==C.TRACK_TYPE_TEXT){summary.append(f.language==null?"und":f.language).append(" · ").append(f.sampleMimeType).append(g.isTrackSelected(n)?" ✓":"").append("\n");if(g.isTrackSelected(n))trackLanguage=f.language;}if(g.isTrackSelected(n)&&g.getType()==C.TRACK_TYPE_VIDEO){
 String codecs=f.codecs==null?"":f.codecs;
 boolean chroma422=VideoDecoderCompatibility.isKnown422CodecString(codecs);
 prefs().edit().putString("osd_video",f.width+"×"+f.height+" · "+codecs+(chroma422?" · 4:2:2 profile":""))
  .putBoolean("iptv_stream_422_profile",chroma422).apply();
 }if(g.isTrackSelected(n)&&g.getType()==C.TRACK_TYPE_AUDIO)prefs().edit().putString("osd_audio",(f.language==null?"":f.language)+" · "+f.channelCount+"ch · "+f.codecs).apply();}prefs().edit().putString("iptv_tracks",summary.length()==0?tx("لا يوجد مسار ترجمة منشور","No subtitle track exposed"):summary.toString()).apply();}@Override public void onPlaybackStateChanged(int state){status.setText(state==Player.STATE_BUFFERING?tx("جارٍ التخزين المؤقت…","Buffering…"):state==Player.STATE_READY?tx("بث IPTV عبر Media3","IPTV playback through Media3")+(prefs().getBoolean("iptv_stream_422_profile",false)?" · 4:2:2 profile":""):state==Player.STATE_ENDED?tx("انتهى البث","Stream ended"):"");}@Override public void onPlayerError(PlaybackException error){
 String code=error.getErrorCodeName();
 String diagnostic=error.getMessage()==null?code:error.getMessage();if(diagnostic!=null&&diagnostic.length()>400)diagnostic=diagnostic.substring(0,400);
 prefs().edit().putString("decoder_last_error",diagnostic==null?"Unknown playback error":diagnostic).putLong("decoder_last_error_at",System.currentTimeMillis()).apply();
 String hint=code!=null&&(code.contains("DECODER_INIT")||code.contains("DECODING_FAILED"))?
  tx(" قد لا يدعم مفكك الجهاز صيغة الفيديو أو عمق اللون؛ افحص تقرير 4:2:2."," The device decoder may not support this video profile or bit depth; inspect the 4:2:2 report."):"";
 status.setText(tx("تعذر البث: ","Playback failed: ")+code+hint);
 // AVC High422 is a positive hint. For HEVC, the codec string alone
 // does not prove 4:2:2; use LibVLC only after an actual decoder error.
 // One handoff per stream prevents infinite HW/SW fallback loops.
 boolean hevcDecoderFailed="video/hevc".equals(
   prefs().getString("decoder_input_mime",""));
 if(!softwareFallbackStarted&&
    (prefs().getBoolean("decoder_input_profile422",false)||hevcDecoderFailed)
    &&code!=null&&(code.contains("DECODER")||code.contains("DECODING"))){
  softwareFallbackStarted=true;
  handler.post(()->{
   if(closed||channel==null||!PlaylistRepository.validStream(channel.uri))return;
   prefs().edit().putString("decoder_422_verification",
     "MEDIA3_CODEC_FAILED_TRYING_LIBVLC").apply();
   openSoftwareDecoder();
   finish();
  });
 }
}});io.execute(()->{PlaylistRepository repo=new PlaylistRepository(this);List<PlaylistRepository.Channel> visible=new ArrayList<>();for(PlaylistRepository.Channel c:repo.all())if(!c.hidden)visible.add(c);PlaylistRepository.Channel selected=repo.find(getIntent().getStringExtra("channelId"));if(selected==null&&PlaylistRepository.validStream(getIntent().getStringExtra("channelUri"))){selected=new PlaylistRepository.Channel();selected.uri=getIntent().getStringExtra("channelUri");selected.id="stream:"+Integer.toHexString(selected.uri.hashCode());selected.name=getIntent().getStringExtra("channelName");if(selected.name==null)selected.name="Personal stream";}final PlaylistRepository.Channel picked=selected;runOnUiThread(()->{if(closed)return;library=repo;channels=visible;if(picked!=null)play(picked);else status.setText(tx("استورد قائمة قنواتك أولاً","Import your playlist first"));});});handler.post(clock);}
 private void openSoftwareDecoder(){if(channel==null||!PlaylistRepository.validStream(channel.uri)){toast(tx("لا يوجد رابط بث صالح","No valid stream URI"));return;}Intent intent=new Intent(this,SoftwareVideoActivity.class).putExtra("media_uri",channel.uri).putExtra("channel",channel.id);startActivity(intent);}
 private void flushDecoderTelemetry(){lastTelemetryFlush=System.currentTimeMillis();prefs().edit().putLong("decoder_processed_frames",telemetryFrames.get()).putLong("decoder_processing_offset_us",telemetryOffsetUs.get()).putLong("decoder_dropped_frames",telemetryDropped.get()).putLong("decoder_dropped_frame_events",telemetryDropEvents.get()).putLong("decoder_last_drop_elapsed_ms",telemetryLastDropElapsedMs.get()).apply();}
 private void resize(int delta){CaptionAppearance.set(this,CaptionAppearance.size(this)+delta);applyCaptionStyle();toast(CaptionAppearance.size(this)+" sp");}
 private void applyCaptionStyle(){if(caption!=null)CaptionAppearance.apply(caption);if(styledView!=null)CaptionAppearance.apply(styledView);if(surface!=null)CaptionAppearance.apply(surface.getSubtitleView());}
 private void small(LinearLayout row,String label,Runnable action){Button b=button(label);b.setTextSize(13);b.setOnClickListener(v->action.run());row.addView(b,new LinearLayout.LayoutParams(0,dp(47),1));}
 private void play(PlaylistRepository.Channel item){softwareFallbackStarted=false;generation++;if(live!=null)live.clear();channel=item;prefs().edit().putString("decoder_channel",item.id).putInt("decoder_width",0).putInt("decoder_height",0).putBoolean("iptv_stream_422_profile",false).putString("decoder_name","").putString("decoder_kind","UNKNOWN").putLong("decoder_init_ms",-1).putLong("decoder_first_frame_at",0).putString("decoder_input_mime","").putString("decoder_input_codecs","").putBoolean("decoder_input_profile422",false).putString("decoder_422_verification","WAITING_FOR_FORMAT").putLong("decoder_processed_frames",0).putLong("decoder_processing_offset_us",0).putLong("decoder_dropped_frames",0).putLong("decoder_dropped_frame_events",0).putLong("decoder_last_drop_elapsed_ms",0).remove("decoder_last_error").apply();telemetryFrames.set(0);telemetryOffsetUs.set(0);telemetryDropped.set(0);telemetryDropEvents.set(0);telemetryLastDropElapsedMs.set(0);lastTelemetryFlush=System.currentTimeMillis();programmeKey="";cues=Collections.emptyList();styled=null;if(styledView!=null)styledView.setCues(Collections.emptyList());assNative=false;caption.setText("");title.setText(item.name);player.setMediaItem(MediaItem.fromUri(item.uri));player.prepare();player.play();publish();surface.requestFocus();File saved=new File(getFilesDir(),"iptv-caption-"+fileKey(item.id)+".txt");if(saved.isFile())load(saved);}
 private void change(int step){if(channels.isEmpty()||channel==null)return;int index=0;for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(channel.id))index=i;play(channels.get((index+step+channels.size())%channels.size()));}
 private void publish(){lastPublish=System.currentTimeMillis();if(channel==null||library==null)return;long now=lastPublish;PlaylistRepository.Programme current=library.now(channel,now),next=null;for(PlaylistRepository.Programme p:library.schedule(channel))if(p.startMs>now&&(next==null||p.startMs<next.startMs))next=p;SatelliteState state=new SatelliteState();state.channelId=channel.id;state.channelName=channel.name;state.source="IPTV / XMLTV";state.observedAt=now;if(current!=null){state.title=current.title;state.image=current.image;state.startMs=current.startMs;state.endMs=current.endMs;}if(next!=null){state.nextTitle=next.title;state.nextImage=next.image;state.nextStartMs=next.startMs;state.nextEndMs=next.endMs;}String key=state.channelId+"|"+state.startMs+"|"+state.title;if(!key.equals(programmeKey)){programmeKey=key;state.save(this);profile=state.title.isEmpty()?"channel:"+channel.id:"film:"+PlaylistRepository.normalize(state.title);delay=prefs().getLong("subtitle_offset:"+profile,0);timing.setText(tx("تأخير: ","Offset: ")+delay+" ms");}prefs().edit().putLong("satellite_observed_at",now).apply();}
 private void subtitles(){new android.app.AlertDialog.Builder(this).setItems(new String[]{tx("استيراد SRT / ASS / VTT","Import SRT / ASS / VTT"),tx("استخدام الملف المحفوظ من مزود الترجمة","Use downloaded subtitle file"),tx("إخفاء الترجمة","Hide subtitles")},(d,w)->{if(w==0)launchDocument(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),701);else if(w==1)load(new File(getFilesDir(),SubtitleStorage.FILE));else{cues=Collections.emptyList();styled=null;if(live!=null)live.clear();styledView.setCues(Collections.emptyList());caption.setVisibility(View.INVISIBLE);player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}}).show();}
 private void load(File file){final int token=++generation;io.execute(()->{try(InputStream in=new FileInputStream(file)){String content=PlaylistRepository.readBounded(in);List<SubtitleParser.Cue> parsed=SubtitleParser.parse(content);if(parsed.isEmpty())throw new IOException("No valid subtitle cues");boolean ass=content.contains("[Events]");StyledSubtitles formatted=new StyledSubtitles(content);runOnUiThread(()->{if(closed||token!=generation)return;cues=parsed;styled=formatted;boolean downloaded=file.getName().equals(SubtitleStorage.FILE);if(downloaded)profile=prefs().getString("subtitle_profile",profile);String fileIdentity=downloaded?prefs().getString("subtitle_file_identity",SubtitleSyncRepository.digest(content)):SubtitleSyncRepository.digest(content);SubtitleSyncRepository.bind(this,profile,fileIdentity,downloaded?prefs().getString("subtitle_provider","Downloaded"):"Local",downloaded?prefs().getString("subtitle_display_name",file.getName()):prefs().getString("iptv_caption_name:"+channel.id,file.getName()));delay=SubtitleSyncRepository.offset(this);timing.setText(tx("تأخير: ","Offset: ")+delay+" ms");assNative=false;caption.setVisibility(View.INVISIBLE);player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());toast(tx("تم تحميل الترجمة","Subtitles loaded"));});}catch(Exception e){runOnUiThread(()->toast(tx("تعذر قراءة الترجمة: ","Subtitle error: ")+e.getMessage()));}});}
 private static String fileKey(String value){try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 private void offset(long change){delay=SubtitleSyncRepository.adjust(this,change);if(assNative){assNative=false;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}timing.setText(tx("تأخير: ","Offset: ")+delay+" ms");}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=701||result!=RESULT_OK||data==null||data.getData()==null||channel==null)return;Uri uri=data.getData();final String importedChannel=channel.id;File target=new File(getFilesDir(),"iptv-caption-"+fileKey(channel.id)+".txt");io.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){String text=PlaylistRepository.readBounded(in);if(SubtitleParser.parse(text).isEmpty())throw new IOException("Invalid subtitle");try(FileOutputStream out=new FileOutputStream(target)){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}String displayName=uri.getLastPathSegment();try(android.database.Cursor row=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(row!=null&&row.moveToFirst())displayName=row.getString(0);}catch(Exception ignored){}if(displayName!=null)prefs().edit().putString("iptv_caption_name:"+importedChannel,displayName.substring(0,Math.min(240,displayName.length()))).apply();runOnUiThread(()->{if(!closed&&channel!=null&&channel.id.equals(importedChannel))load(target);});}catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}});}
 @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_CHANNEL_UP){change(1);return true;}if(key==KeyEvent.KEYCODE_CHANNEL_DOWN){change(-1);return true;}return super.onKeyDown(key,event);}
 private void beginVideoBudget(){
  if(!resourcesHeld){PlaybackResourceMode.enter(this);resourcesHeld=true;}
 }
 private void endVideoBudget(){
  if(resourcesHeld){PlaybackResourceMode.leave(this);resourcesHeld=false;}
 }
 @Override public void onTrimMemory(int level){
  super.onTrimMemory(level);
  PlaybackResourceMode.memoryPressure(this,level);
 }
 @Override protected void onResume(){super.onResume();beginVideoBudget();applyCaptionStyle();PlaybackClock.visible=true;prefs().edit().putBoolean("iptv_player_visible",true).putBoolean("satellite_tv_visible",true).apply();if(player!=null&&resume)player.play();}
 @Override protected void onPause(){endVideoBudget();PlaybackClock.visible=false;prefs().edit().putBoolean("iptv_player_visible",false).putBoolean("satellite_tv_visible",false).apply();if(player!=null){resume=player.isPlaying();player.pause();}super.onPause();}
 @Override protected void onDestroy(){endVideoBudget();closed=true;generation++;handler.removeCallbacksAndMessages(null);io.shutdownNow();if(live!=null)live.close();if(player!=null){surface.setPlayer(null);player.release();}flushDecoderTelemetry();super.onDestroy();}
}
