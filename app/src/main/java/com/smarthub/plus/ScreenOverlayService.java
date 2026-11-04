package com.smarthub.plus;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** User-created visual layers. This service never captures or edits broadcaster pixels/audio. */
public final class ScreenOverlayService extends Service {
    public static final String ACTION_APPLY="com.smarthub.plus.screen.APPLY", ACTION_STOP="com.smarthub.plus.screen.STOP",
            ACTION_CAPTION="com.smarthub.plus.screen.CAPTION", ACTION_PARENTAL="com.smarthub.plus.screen.PARENTAL", ACTION_EDIT="com.smarthub.plus.screen.EDIT";
    public static final String EXTRA_PLAY="play", EXTRA_SEEK_MS="seek", EXTRA_RESET="reset", EXTRA_EDITING="editing", EXTRA_ENABLED="enabled";
    private static final String CHANNEL="screen_tools";
    private static final int NOTIFICATION=4201;
    private WindowManager manager; private SharedPreferences prefs; private final List<View> overlays=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper()); private final ExecutorService files=Executors.newSingleThreadExecutor();
    private TextView caption;private androidx.media3.ui.SubtitleView styledView;private StyledSubtitles styled; private View parental; private List<SubtitleParser.Cue> cues=Collections.emptyList();
    private long captionPosition,captionStartedAt,captionPlayerAnchor; private boolean playing,editing,destroyed; private int generation;
    private final Runnable clock=new Runnable(){@Override public void run(){if(destroyed)return;if(caption!=null){CaptionAppearance.apply(caption);CaptionAppearance.apply(styledView);long position=positionMs()-prefs.getLong("subtitle_offset",0);String text=SubtitleParser.textAt(cues,position);CaptionClock.positionMs=positionMs();CaptionClock.playing=playing;CaptionClock.text=text;if(styledView!=null){styledView.setCues(styled==null?java.util.Collections.emptyList():styled.at(position));}if(!text.contentEquals(caption.getText()))caption.setText(text);boolean outsideSatellite=prefs.getBoolean("satellite_companion_enabled",false)&&!prefs.getString("subtitle_channel_id","").isEmpty()&&((!prefs.getBoolean("satellite_tv_visible",false)&&!prefs.getBoolean("iptv_player_visible",false))||prefs.getBoolean("satellite_companion_expanded",false));boolean show=!text.isEmpty()&&!outsideSatellite&&prefs.getBoolean("overlay_caption",false);caption.setVisibility(styled==null&&show?View.VISIBLE:View.INVISIBLE);if(styledView!=null)styledView.setVisibility(show?View.VISIBLE:View.INVISIBLE);}handler.postDelayed(this,100);}};
    @Override public void onCreate(){super.onCreate();manager=(WindowManager)getSystemService(WINDOW_SERVICE);prefs=getSharedPreferences("smarthub",MODE_PRIVATE);NotificationChannel channel=new NotificationChannel(CHANNEL,"Screen tools",NotificationManager.IMPORTANCE_LOW);channel.setDescription("Visible user-configured logo, text, captions and mask layers");getSystemService(NotificationManager.class).createNotificationChannel(channel);handler.post(clock);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        foreground();String action=intent.getAction();
        if(ACTION_STOP.equals(action)){prefs.edit().putBoolean("overlay_logo",false).putBoolean("overlay_text",false).putBoolean("overlay_caption",false).putBoolean("overlay_mask",false).putBoolean("overlay_parental",false).apply();stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){prefs.edit().putString("screen_error","Overlay permission is required").apply();stopSelf();return START_NOT_STICKY;}
        if(intent.hasExtra(EXTRA_EDITING))editing=intent.getBooleanExtra(EXTRA_EDITING,false);
        if(ACTION_EDIT.equals(action)){if(overlays.isEmpty())rebuild();else if(parental!=null)parental.setVisibility(editing?View.GONE:View.VISIBLE);return START_NOT_STICKY;}
        if(ACTION_PARENTAL.equals(action))prefs.edit().putBoolean("overlay_parental",intent.getBooleanExtra(EXTRA_ENABLED,false)).apply();
        if(ACTION_CAPTION.equals(action)){
            long position=positionMs();if(intent.getBooleanExtra(EXTRA_RESET,false))position=0;if(intent.hasExtra(EXTRA_SEEK_MS))position=Math.max(0,intent.getLongExtra(EXTRA_SEEK_MS,0));
            captionPosition=position;captionPlayerAnchor=PlaybackClock.positionMs;captionStartedAt=SystemClock.elapsedRealtime();if(intent.hasExtra(EXTRA_PLAY))playing=intent.getBooleanExtra(EXTRA_PLAY,false);
        }
        rebuild();return START_NOT_STICKY;
    }
    private long positionMs(){if(!playing)return captionPosition;if(PlaybackClock.visible&&PlaybackClock.channelId.equals(prefs.getString("subtitle_channel_id","")))return captionPosition+Math.max(0,PlaybackClock.positionMs-captionPlayerAnchor);return captionPosition+SystemClock.elapsedRealtime()-captionStartedAt;}
    private void foreground(){Notification notification=notification();if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(NOTIFICATION,notification);}
    private Notification notification(){
        Intent controls=new Intent(this,ScreenToolsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(this,0,controls,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=prefs.getBoolean("overlay_parental",false)?open:PendingIntent.getService(this,1,new Intent(this,ScreenOverlayService.class).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("IRAQhub · Screen tools")
                .setContentText(prefs.getBoolean("overlay_parental",false)?"Parental mask · open controls to unlock":"Logo, text, captions and mask controls")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop / controls",stop).build()).build();
    }
    private void rebuild(){
        removeOverlays();int token=++generation;
        boolean any=prefs.getBoolean("overlay_logo",false)||prefs.getBoolean("overlay_text",false)||prefs.getBoolean("overlay_caption",false)||prefs.getBoolean("overlay_mask",false)||prefs.getBoolean("overlay_parental",false);
        if(!any){stopSelf();return;}
        try{
            if(prefs.getBoolean("overlay_mask",false)){View mask=new View(this);int color;try{color=Color.parseColor(prefs.getString("overlay_mask_color","#000000"));}catch(Exception ignored){color=Color.BLACK;}mask.setBackgroundColor(color);add(mask,"mask",0,85,100,15);}
            if(prefs.getBoolean("overlay_logo",false)){
                ImageView logo=new ImageView(this);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);add(logo,"logo",3,3,12,12);
                files.execute(()->{Bitmap bitmap=BitmapFactory.decodeFile(new File(getFilesDir(),"overlay_logo.png").getAbsolutePath());handler.post(()->{if(!destroyed&&token==generation&&bitmap!=null)logo.setImageBitmap(bitmap);});});
            }
            if(prefs.getBoolean("overlay_text",false)){
                String text=prefs.getString("overlay_text_value","");View view;
                if(prefs.getBoolean("overlay_text_scroll",false))view=new ScrollingText(this,text,prefs.getInt("overlay_text_font",24));
                else {TextView staticText=textView(prefs.getInt("overlay_text_font",24));staticText.setText(text);view=staticText;}
                view.setBackgroundColor(0xd9081623);add(view,"text",3,84,94,8);
            }
            if(prefs.getBoolean("overlay_caption",false)){
                caption=textView(CaptionAppearance.size(this));CaptionAppearance.apply(caption);caption.setBackgroundColor(0xc9000000);caption.setMaxLines(4);add(caption,"caption",8,70,84,14);
                styledView=new androidx.media3.ui.SubtitleView(this);add(styledView,"caption",8,70,84,14);files.execute(()->{try{String raw=SubtitleStorage.readInternal(this);List<SubtitleParser.Cue> loaded=SubtitleParser.parse(raw);StyledSubtitles st=new StyledSubtitles(raw);handler.post(()->{if(!destroyed&&token==generation){cues=loaded;styled=st;}});}catch(Exception error){prefs.edit().putString("screen_error","Subtitle file: "+error.getMessage()).apply();}});
            }
            if(prefs.getBoolean("overlay_parental",false)){
                parental=new View(this);parental.setBackgroundColor(Color.BLACK);WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;manager.addView(parental,p);overlays.add(parental);parental.setVisibility(editing?View.GONE:View.VISIBLE);
            }
            prefs.edit().putBoolean("screen_running",true).putString("screen_error","").apply();getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());
        }catch(Exception error){prefs.edit().putString("screen_error",error.getMessage()).apply();stopSelf();}
    }
    private TextView textView(int sp){TextView view=new TextView(this);view.setTextColor(Color.WHITE);view.setTextSize(sp);view.setTypeface(null,Typeface.BOLD);view.setGravity(Gravity.CENTER);view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);view.setPadding(dp(8),dp(3),dp(8),dp(3));return view;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void add(View view,String tool,int defaultX,int defaultY,int defaultW,int defaultH){
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(metrics);
        int w=clamp(prefs.getInt("overlay_"+tool+"_w",defaultW),1,100)*metrics.widthPixels/100,h=clamp(prefs.getInt("overlay_"+tool+"_h",defaultH),1,100)*metrics.heightPixels/100;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=Math.min(metrics.widthPixels-w,clamp(prefs.getInt("overlay_"+tool+"_x",defaultX),0,100)*metrics.widthPixels/100);p.y=Math.min(metrics.heightPixels-h,clamp(prefs.getInt("overlay_"+tool+"_y",defaultY),0,100)*metrics.heightPixels/100);p.alpha=clamp(prefs.getInt("overlay_"+tool+"_alpha",tool.equals("mask")?85:100),5,100)/100f;
        manager.addView(view,p);overlays.add(view);
    }
    private int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private void removeOverlays(){for(View view:overlays)try{manager.removeView(view);}catch(Exception ignored){}overlays.clear();caption=null;styledView=null;styled=null;parental=null;}
    @Override public void onDestroy(){destroyed=true;generation++;handler.removeCallbacksAndMessages(null);files.shutdownNow();removeOverlays();prefs.edit().putBoolean("screen_running",false).apply();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private static final class ScrollingText extends View {
        private final StaticLayout layout; private final int textWidth;private float x;private long last;
        ScrollingText(Context context,String text,int sp){super(context);TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.WHITE);paint.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);paint.setTypeface(Typeface.DEFAULT_BOLD);textWidth=Math.max(1,(int)Math.ceil(paint.measureText(text)));layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,textWidth+8).setMaxLines(1).setIncludePad(false).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build();}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){x=w;last=0;}
        @Override protected void onDraw(Canvas canvas){long now=SystemClock.elapsedRealtime();if(last!=0)x-=(now-last)*0.065f*getResources().getDisplayMetrics().density;last=now;if(x < -textWidth)x=getWidth();int save=canvas.save();canvas.translate(x,(getHeight()-layout.getHeight())/2f);layout.draw(canvas);canvas.restoreToCount(save);postInvalidateDelayed(20);}
    }
}
