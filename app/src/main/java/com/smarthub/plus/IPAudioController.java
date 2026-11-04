package com.smarthub.plus;
import android.content.Context;import android.os.*;import androidx.media3.common.*;import androidx.media3.exoplayer.ExoPlayer;
/** Real network audio, independent of the OEM catalogue. DVB is restored on stop or error. */
final class IPAudioController implements AutoCloseable {
 interface NativeSound{void enable(boolean enabled);}
 private final Context context;private final NativeSound sound;private final Handler main=new Handler(Looper.getMainLooper());private ExoPlayer player;private String identity="",config="";private int generation;private final java.util.concurrent.ExecutorService io=java.util.concurrent.Executors.newSingleThreadExecutor();private int lookup;private boolean closed;
 IPAudioController(Context c,NativeSound sound){context=c.getApplicationContext();this.sound=sound;}
 void apply(String physical){if(closed)return;int ticket=++lookup;io.execute(()->{IPAudioBinding binding=IPAudioBinding.read(context,physical);main.post(()->{if(!closed&&ticket==lookup)prepared(physical,binding);});});}
 private void prepared(String physical,IPAudioBinding binding){String tag=binding==null?"":binding.url+"|"+binding.delay+"|"+binding.volume+"|"+binding.enabled;if(identity.equals(physical)&&config.equals(tag))return;stop();identity=physical;config=tag;if(binding==null||!binding.enabled)return;int token=++generation;player=new ExoPlayer.Builder(context).build();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);player.setVolume(context.getSharedPreferences("smarthub",0).getBoolean("dvb_muted",false)?0:binding.volume/100f);player.addListener(new Player.Listener(){boolean ready;public void onPlaybackStateChanged(int state){if(token!=generation)return;if(state==Player.STATE_READY&&!ready){ready=true;main.postDelayed(()->{if(token==generation&&player!=null){sound.enable(false);player.play();}},binding.delay);}if(state==Player.STATE_ENDED)stop();publish(state==Player.STATE_BUFFERING?"IP_AUDIO_BUFFERING":state==Player.STATE_READY?"IP_AUDIO_READY":"");}public void onIsPlayingChanged(boolean playing){if(token==generation&&playing)publish("IP_AUDIO_PLAYING");}public void onPlayerError(PlaybackException error){stop();publish("IP_AUDIO_FAILED");}});publish("IP_AUDIO_CONNECTING");player.setMediaItem(MediaItem.fromUri(binding.url));player.prepare();}
 private void publish(String status){context.getSharedPreferences("smarthub",0).edit().putString("ip_audio_status",status).apply();}
 void stop(){lookup++;generation++;main.removeCallbacksAndMessages(null);if(player!=null){player.release();player=null;sound.enable(true);}identity="";config="";publish("");}
 void remoteVolume(int percent,boolean muted){if(player!=null)player.setVolume(muted?0:Math.max(0,Math.min(100,percent))/100f);}
 boolean playing(){return player!=null&&player.isPlaying();}
 public void close(){closed=true;stop();io.shutdownNow();}
}
