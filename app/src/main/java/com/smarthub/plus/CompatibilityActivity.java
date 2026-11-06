package com.smarthub.plus;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.tv.TvContract;
import android.database.Cursor;
import android.os.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Public diagnostics only; no serials, credentials or access to protected vendor files. */
public final class CompatibilityActivity extends HubActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();private volatile boolean destroyed;
    @Override public void onCreate(Bundle s){super.onCreate(s);LinearLayout body=page(tx("توافق الجهاز ومصادر البيانات","Device compatibility and data sources"));TextView report=text(tx("فحص الواجهات…","Inspecting interfaces…"),16,false);card(body).addView(report);io.execute(()->{String r=inspect(this);runOnUiThread(()->{if(!destroyed)report.setText(r);});});}
    static String inspect(Context c){
        boolean ar=c.getSharedPreferences("smarthub",0).getBoolean("arabic",true);StringBuilder b=new StringBuilder("Android "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT+"\n"+Build.MANUFACTURER+" "+Build.MODEL+"\nSoC: "+(Build.VERSION.SDK_INT>=31?Build.SOC_MANUFACTURER+" "+Build.SOC_MODEL:Build.HARDWARE)+"\nABI: "+String.join(", ",Build.SUPPORTED_ABIS));
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)c.getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);b.append(String.format(Locale.ROOT,"\nRAM: %.1f GB · available %.1f GB",memory.totalMem/1073741824.0,memory.availMem/1073741824.0));
        b.append("\n\nAndroid TV channels: ");try(Cursor cursor=c.getContentResolver().query(TvContract.Channels.CONTENT_URI,new String[]{"_id"},null,null,null)){b.append(cursor==null?"unavailable":cursor.getCount());}catch(SecurityException e){b.append("permission denied");}catch(Exception e){b.append("unavailable");}
        b.append("\nVendor program index: ").append(c.checkSelfPermission("com.tosmart.dvb.common.permission.READ_PROGRAM_INDEX")==PackageManager.PERMISSION_GRANTED?"permission granted":"manufacturer permission unavailable");
        b.append("\nNative TV: ").append(ReceiverNavigation.available(c,ReceiverNavigation.television()));b.append("\nNative menu: ").append(ReceiverNavigation.available(c,ReceiverNavigation.menu()));b.append("\nNative EPG: ").append(ReceiverNavigation.available(c,ReceiverNavigation.guide()));
        b.append("\n\n").append(ar?"الإشارة: ":"Signal: ").append(DirectReceiverSignal.sourceLabel(SatelliteState.read(c),ar));b.append("\n").append(ReceiverDvbBridge.label(c));
        DecoderDimensions nativeSize=DecoderDimensions.read();long nativeFrames=DecoderFrames.read();
        b.append("\n").append(ar?"مراقبة VDEC الأصلية: ":"Native VDEC telemetry: ");
        if(nativeSize!=null)b.append(nativeSize.width).append("×").append(nativeSize.height);
        else b.append(ar?"الدقة غير متاحة":"resolution unavailable");
        if(nativeFrames>=0)b.append(" · frames=").append(nativeFrames);
        else b.append(ar?" · عداد الإطارات غير متاح":" · frame counter unavailable");
        b.append(ar?"\n\nPAT / PMT / SDT / NIT / EIT الخام: لا توجد واجهة خام متاحة للتطبيق في SDK المفحوص. معلومات EPG من المصادر المنشورة أو XMLTV، لا من تحليل جداول خام.\nAGC وCAS وHDR لا تُعرض بقيم مخمّنة. إدارة القنوات وLNB والبحث تتم في أدوات المصنع.\nCHL: أسماء ومزود وترددات محفوظة؛ ليست حالة الموالف الحالية.\nCHMAX: إضافة صور اختيارية؛ تستخدم هوية القناة ومصادر البيانات المشتركة.":"\n\nRaw PAT / PMT / SDT / NIT / EIT: no raw interface is exposed to this app by the inspected SDK. EPG uses published sources or XMLTV, not raw table parsing.\nAGC, CAS and HDR are not guessed. Channel editing, LNB and scanning use manufacturer tools.\nCHL: saved names, providers and transponders, not live tuner state.\nCHMAX: optional artwork integration using the shared channel identity.");
        try{
            VideoDecoderCompatibility.Result video=VideoDecoderCompatibility.inspect();
            b.append("\n\n").append(ar?"توافق الفيديو 4:2:2:\n":"Video 4:2:2 compatibility:\n");
            b.append(video.summary()).append('\n');
            for(String item:video.hardware422)b.append("HW · ").append(item).append('\n');
            for(String item:video.software422)b.append("SW · ").append(item).append('\n');
            b.append(ar?"\nمفككات الفيديو المنشورة:\n":"\nExposed video decoders:\n");
            for(String item:video.videoDecoders)b.append(item).append('\n');
            android.content.SharedPreferences p=c.getSharedPreferences("smarthub",0);
            b.append("\n").append(ar?"جلسة فك الترميز الأخيرة:\n":"Latest decoder session:\n");
            b.append(ar?"Decoder: ":"Decoder: ").append(p.getString("decoder_name",ar?"لم يبدأ":"not started")).append(" · ").append(p.getString("decoder_kind","UNKNOWN"));
            long init=p.getLong("decoder_init_ms",-1);if(init>=0)b.append(" · init ").append(init).append(" ms");
            String mime=p.getString("decoder_input_mime","");String codecs=p.getString("decoder_input_codecs","");
            if(!mime.isEmpty()||!codecs.isEmpty())b.append("\nInput: ").append(mime).append(" · ").append(codecs).append(" · ").append(p.getInt("decoder_input_width",0)).append("×").append(p.getInt("decoder_input_height",0));
            b.append("\n4:2:2 status: ").append(p.getString("decoder_422_verification","NO_SESSION"));
            b.append("\nDVB VDEC frame health: ").append(p.getString("decoder_video_health","NOT_MEASURED"));
            b.append(ar?"\nهذه قراءة عداد الإطارات من النظام ولا تثبت ظهور صورة 4:2:2 سليمة. عدم توفرها لا يعني فشل فك الشفرة.":"\nFirmware-wide frame telemetry does not prove a visible 4:2:2 picture. Missing telemetry is not a decode or BISS failure.");
            if(p.getLong("decoder_first_frame_at",0)>0)b.append("\nFirst frame rendered: yes");else b.append("\nFirst frame rendered: not confirmed");
            long processed=p.getLong("decoder_processed_frames",0);
            long offset=p.getLong("decoder_processing_offset_us",0);
            long dropped=p.getLong("decoder_dropped_frames",0);
            b.append("\nProcessed frame samples: ").append(processed);
            b.append(" · processing offset total: ").append(offset).append(" us");
            if(processed>0)b.append(" · average offset/sample: ").append(offset/processed).append(" us");
            b.append("\nDropped video frames: ").append(dropped)
             .append(" · drop events: ").append(p.getLong("decoder_dropped_frame_events",0));
            if(p.getLong("decoder_session_started_at",0)>0)b.append("\nSession started: ").append(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(p.getLong("decoder_session_started_at",0))));
            String decoderError=p.getString("decoder_last_error","");if(!decoderError.isEmpty())b.append("\nLast codec error: ").append(decoderError);
            b.append("\n").append(ar?"ملاحظة: ظهور أول إطار يثبت بدء العرض فقط، ولا يثبت سلامة عينات 4:2:2 أو كل الإطارات.":"Note: first-frame rendering confirms presentation started; it does not prove 4:2:2 bitstream integrity or sustained playback.");
        }catch(Exception unavailable){b.append("\nVideo decoder inspection unavailable");}
        return b.toString();
    }
    @Override protected void onDestroy(){destroyed=true;io.shutdownNow();super.onDestroy();}
}
