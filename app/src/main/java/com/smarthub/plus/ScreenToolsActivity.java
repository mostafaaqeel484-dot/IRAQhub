package com.smarthub.plus;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Position controls remain in the app; visible layers never consume remote-control focus. */
public final class ScreenToolsActivity extends HubActivity {
    private static final int LOGO=401,SUBTITLE=402;
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private final List<ToolFields> tools=new ArrayList<>();
    private TextView permission,status,parentStatus;
    private EditText textValue,textFont,captionFont,captionOffset,seekSeconds,maskColor,currentPin,newPin;
    private CheckBox scrolling; private ToolFields captionTool;
    private boolean destroyed;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout body=page(tx("أدوات الشاشة", "Screen tools"),tx("شعار ونص وترجمة وقناع بصري فوق التطبيقات", "Place a logo, text, captions and a visual mask over apps"));
        LinearLayout controls=card(body);permission=text("",15,true);controls.addView(permission);
        action(controls,tx("منح إذن الظهور فوق التطبيقات", "Allow display over other apps"),this::requestOverlay);
        note(controls,tx("الموضع والحجم بنسبة مئوية من الشاشة: X من اليسار، Y من الأعلى. تتغير الأدوات من هذه الحقول ولا تستحوذ على الريموت. القناع يغطي منطقة تختارها بلون؛ لا يستخرج ولا يزيل النص الأصلي من بث القناة.", "Position and size use screen percentages: X from the left, Y from the top. Adjust tools here; overlay layers do not take remote focus. A mask covers your chosen region with a colour; it does not extract or remove embedded broadcaster text."));
        status=text("",14,false);controls.addView(status);
        LinearLayout logo=card(body);logo.addView(text(tx("شعار مخصص", "Custom logo"),20,true));
        action(logo,tx("اختيار صورة شعار من الجهاز", "Choose logo image"),()->pick(LOGO,"image/*"));
        new ToolFields(logo,"logo",tx("عرض الشعار", "Show logo"),3,3,12,12,100);
        LinearLayout text=card(body);text.addView(text(tx("النص والشريط المتحرك", "Text and scrolling bar"),20,true));
        textValue=field(text,tx("النص الذي يظهر على الشاشة", "Text to show on screen"),prefs().getString("overlay_text_value",""));
        scrolling=check(text,tx("تمرير النص من اليمين إلى اليسار", "Scroll text from right to left"),prefs().getBoolean("overlay_text_scroll",true));
        textFont=field(text,tx("حجم النص 12–64", "Text size 12–64"),Integer.toString(prefs().getInt("overlay_text_font",24)));numeric(textFont,false);
        new ToolFields(text,"text",tx("عرض النص", "Show text"),3,84,94,8,95);
        LinearLayout subtitles=card(body);subtitles.addView(text(tx("ترجمة ملف على الشاشة", "Subtitle file on screen"),20,true));
        action(subtitles,tx("استيراد ملف SRT / VTT", "Import SRT / VTT file"),()->pick(SUBTITLE,"*/*"));
        note(subtitles,tx("يُستخدم آخر ملف استوردته هنا أو ترجمته في صفحة الترجمة. هذا مؤقت يدوي مستقل عن صوت القناة؛ اضبطه على توقيت البرنامج.", "Uses the last file imported here or translated in Subtitle translation. This is a manual timer independent of channel audio; sync it to the programme."));
        captionFont=field(subtitles,tx("حجم الترجمة 12–64", "Caption size 12–64"),Integer.toString(prefs().getInt("overlay_caption_font",28)));numeric(captionFont,false);
        captionOffset=field(subtitles,tx("التأخير بالمللي ثانية: موجب يؤخر الترجمة", "Caption delay in milliseconds: positive delays text"),Long.toString(prefs().getLong("subtitle_offset",0)));numeric(captionOffset,true);
        seekSeconds=field(subtitles,tx("البدء من ثانية داخل ملف الترجمة", "Start at this subtitle-file second"),"0");numeric(seekSeconds,false);
        captionTool=new ToolFields(subtitles,"caption",tx("عرض ترجمة الملف", "Show file captions"),8,70,84,14,100);
        action(subtitles,tx("تشغيل من الزمن المحدد", "Play from selected time"),()->captionCommand(true,true,false));
        action(subtitles,tx("متابعة", "Resume"),()->captionCommand(true,false,false));
        action(subtitles,tx("إيقاف مؤقت", "Pause"),()->captionCommand(false,false,false));
        action(subtitles,tx("إعادة المؤقت إلى البداية", "Reset timer"),()->captionCommand(false,false,true));
        subtitles.addView(new SubtitleSyncControls(this));LinearLayout mask=card(body);mask.addView(text(tx("قناع منطقة من الشاشة", "Screen-region mask"),20,true));
        maskColor=field(mask,tx("لون القناع مثل #000000", "Mask colour, e.g. #000000"),prefs().getString("overlay_mask_color","#000000"));
        new ToolFields(mask,"mask",tx("عرض القناع", "Show region mask"),0,85,100,15,85);
        LinearLayout parent=card(body);parent.addView(text(tx("قناع أبوي أسود مع PIN", "Parental black mask with PIN"),20,true));
        parentStatus=text("",15,false);parent.addView(parentStatus);
        note(parent,tx("يغطي الصورة بالكامل عند مغادرة هذه الصفحة. تحتاج إزالته من التطبيق إلى PIN من 4 أرقام. هذا قناع بصري وليس قفل نظام؛ يمكن إيقاف التطبيق من إعدادات Android.", "Covers the picture when you leave this page. Removing it in the app requires a four-digit PIN. This is a visual mask, not an Android system lock; the app can still be stopped in Android settings."));
        currentPin=field(parent,tx("PIN الحالي", "Current PIN"),"");pinInput(currentPin);
        newPin=field(parent,tx("PIN جديد من 4 أرقام", "New four-digit PIN"),"");pinInput(newPin);
        action(parent,tx("حفظ / تغيير PIN", "Save / change PIN"),this::savePin);
        action(parent,tx("تفعيل القناع الأبوي", "Enable parental mask"),()->parental(true));
        action(parent,tx("إلغاء القناع الأبوي", "Disable parental mask"),()->parental(false));
        action(body,tx("حفظ وتطبيق أدوات الشاشة", "Save and apply screen tools"),()->saveAll(true));
        action(body,tx("إيقاف جميع أدوات الشاشة", "Stop all screen tools"),this::stopAll);
        updateStatus();
    }
    private void numeric(EditText input,boolean signed){input.setInputType(InputType.TYPE_CLASS_NUMBER|(signed?InputType.TYPE_NUMBER_FLAG_SIGNED:0));}
    private void pinInput(EditText input){input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});}
    private void requestOverlay(){try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception error){toast(tx("افتح إعدادات Android ثم إذن الظهور فوق التطبيقات", "Open Android settings and allow display over other apps"));}}
    private boolean permitted(){if(Settings.canDrawOverlays(this))return true;toast(tx("امنح إذن الظهور فوق التطبيقات أولاً", "Allow display over other apps first"));requestOverlay();return false;}
    private void command(String action,Intent extra){
        if(!ScreenOverlayService.ACTION_STOP.equals(action)&&!permitted())return;Intent intent=extra==null?new Intent(this,ScreenOverlayService.class):extra;intent.setClass(this,ScreenOverlayService.class).setAction(action).putExtra(ScreenOverlayService.EXTRA_EDITING,true);
        try{startForegroundService(intent);status.setText(tx("طُبقت إعدادات أدوات الشاشة", "Screen tool settings applied"));}catch(Exception error){status.setText(tx("تعذر تشغيل الأدوات: ","Could not start tools: ")+error.getMessage());}
    }
    private void pick(int request,String mime){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);try{launchDocument(intent,request);}catch(Exception error){toast(tx("لا يوجد منتقي ملفات على هذا الجهاز", "No document picker is installed on this device"));}}
    private boolean saveAll(boolean apply){
        try{Color.parseColor(maskColor.getText().toString().trim());}catch(Exception error){toast(tx("لون القناع غير صحيح", "Invalid mask colour"));return false;}
        for(ToolFields fields:tools)if(fields.enabled.isChecked()){
            if(fields.key.equals("logo")&&!new File(getFilesDir(),"overlay_logo.png").isFile()){toast(tx("اختر صورة شعار أولاً", "Choose a logo image first"));return false;}
            if(fields.key.equals("caption")&&!new File(getFilesDir(),SubtitleStorage.FILE).isFile()){toast(tx("استورد ملف ترجمة أولاً", "Import a subtitle file first"));return false;}
            if(fields.key.equals("text")&&textValue.getText().toString().trim().isEmpty()){toast(tx("اكتب النص الذي تريد عرضه", "Enter the text to display"));return false;}
        }
        SharedPreferences.Editor editor=prefs().edit();for(ToolFields fields:tools)fields.save(editor);
        long shift=longNumber(captionOffset,0,-3600000,3600000);String profile=prefs().getString("subtitle_profile","default");
        editor.putString("overlay_text_value",textValue.getText().toString()).putBoolean("overlay_text_scroll",scrolling.isChecked()).putInt("overlay_text_font",number(textFont,24,12,64))
                .putInt("overlay_caption_font",number(captionFont,28,12,64)).putLong("subtitle_offset",shift).putLong("subtitle_offset:"+profile,shift).putString("overlay_mask_color",maskColor.getText().toString().trim()).apply();
        SubtitleSyncRepository.set(this,shift);if(apply)command(ScreenOverlayService.ACTION_APPLY,null);return true;
    }
    private void captionCommand(boolean play,boolean seek,boolean reset){
        if(!new File(getFilesDir(),SubtitleStorage.FILE).isFile()){toast(tx("استورد ملف ترجمة أولاً", "Import a subtitle file first"));return;}
        captionTool.enabled.setChecked(true);if(!saveAll(false))return;
        Intent intent=new Intent().putExtra(ScreenOverlayService.EXTRA_PLAY,play).putExtra(ScreenOverlayService.EXTRA_RESET,reset);
        if(seek)intent.putExtra(ScreenOverlayService.EXTRA_SEEK_MS,longNumber(seekSeconds,0,0,864000)*1000L);
        command(ScreenOverlayService.ACTION_CAPTION,intent);
    }
    private void savePin(){
        if(OverlayGate.configured(prefs())&&!OverlayGate.verify(prefs(),currentPin.getText().toString())){toast(tx("PIN الحالي غير صحيح", "Current PIN is incorrect"));return;}
        String pin=newPin.getText().toString();if(!pin.matches("\\d{4}")){toast(tx("أدخل PIN من 4 أرقام", "Enter a four-digit PIN"));return;}
        OverlayGate.save(prefs(),pin);currentPin.setText(pin);newPin.setText("");updateStatus();toast(tx("حُفظ PIN", "PIN saved"));
    }
    private void parental(boolean enabled){
        if(!permitted())return;
        if(!OverlayGate.configured(prefs())){if(!enabled){toast(tx("لم يُضبط PIN بعد", "No PIN is configured"));return;}String pin=newPin.getText().toString();if(!pin.matches("\\d{4}")){toast(tx("ضع PIN جديداً من 4 أرقام أولاً", "Set a new four-digit PIN first"));return;}OverlayGate.save(prefs(),pin);currentPin.setText(pin);newPin.setText("");}
        if(!OverlayGate.verify(prefs(),currentPin.getText().toString())){toast(tx("PIN غير صحيح", "Incorrect PIN"));return;}
        prefs().edit().putBoolean("overlay_parental",enabled).apply();command(ScreenOverlayService.ACTION_PARENTAL,new Intent().putExtra(ScreenOverlayService.EXTRA_ENABLED,enabled));updateStatus();
    }
    private void stopAll(){
        if(prefs().getBoolean("overlay_parental",false)&&!OverlayGate.verify(prefs(),currentPin.getText().toString())){toast(tx("أدخل PIN لإزالة القناع الأبوي", "Enter the PIN to remove the parental mask"));return;}
        prefs().edit().putBoolean("overlay_parental",false).apply();for(ToolFields fields:tools)fields.enabled.setChecked(false);saveAll(false);command(ScreenOverlayService.ACTION_STOP,null);updateStatus();
    }
    private void updateStatus(){if(permission==null)return;permission.setText(Settings.canDrawOverlays(this)?tx("إذن الظهور فوق التطبيقات: مفعّل", "Display over other apps: allowed"):tx("إذن الظهور فوق التطبيقات: مطلوب", "Display over other apps: required"));parentStatus.setText((OverlayGate.configured(prefs())?tx("PIN مضبوط", "PIN configured"):tx("PIN غير مضبوط", "PIN not configured"))+" · "+(prefs().getBoolean("overlay_parental",false)?tx("القناع مفعّل", "Mask enabled"):tx("القناع متوقف", "Mask disabled")));String error=prefs().getString("screen_error","");if(!error.isEmpty())status.setText(error);}
    @Override protected void onResume(){super.onResume();updateStatus();if(prefs().getBoolean("screen_running",false)&&Settings.canDrawOverlays(this))command(ScreenOverlayService.ACTION_EDIT,new Intent().putExtra(ScreenOverlayService.EXTRA_EDITING,true));}
    @Override protected void onPause(){if(prefs().getBoolean("screen_running",false)&&Settings.canDrawOverlays(this))try{startForegroundService(new Intent(this,ScreenOverlayService.class).setAction(ScreenOverlayService.ACTION_EDIT).putExtra(ScreenOverlayService.EXTRA_EDITING,false));}catch(Exception ignored){}super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();status.setText(tx("جارٍ استيراد الملف…", "Importing file…"));
        files.execute(()->{try{
            if(request==LOGO){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Unsupported image");BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>1024||bounds.outHeight/options.inSampleSize>1024)options.inSampleSize*=2;Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap==null)throw new IOException("Cannot decode image");try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"overlay_logo.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
            else if(request==SUBTITLE){String raw=SubtitleStorage.read(this,uri);List<SubtitleParser.Cue> cues=SubtitleParser.parse(raw);if(cues.isEmpty())throw new IOException("No valid SRT/WebVTT cues");SubtitleStorage.save(this,raw);SubtitleSyncRepository.bind(this,prefs().getString("subtitle_profile","local"),SubtitleSyncRepository.digest(raw),"Local","Imported subtitle");}
            runOnUiThread(()->{if(!destroyed)status.setText(tx("الملف جاهز. فعّل الأداة واضغط حفظ وتطبيق.", "File ready. Enable its tool and press Save and apply."));});
        }catch(Exception error){runOnUiThread(()->{if(!destroyed)status.setText(tx("تعذر الاستيراد: ", "Import failed: ")+error.getMessage());});}});
    }
    private int number(EditText field,int fallback,int min,int max){return(int)longNumber(field,fallback,min,max);}
    private long longNumber(EditText field,long fallback,long min,long max){try{return Math.max(min,Math.min(max,Long.parseLong(field.getText().toString().trim())));}catch(Exception error){return fallback;}}
    @Override protected void onDestroy(){destroyed=true;files.shutdownNow();super.onDestroy();}
    private final class ToolFields{
        final String key;final CheckBox enabled;final EditText x,y,w,h,alpha;
        ToolFields(LinearLayout box,String key,String label,int dx,int dy,int dw,int dh,int da){this.key=key;enabled=check(box,label,prefs().getBoolean("overlay_"+key,false));LinearLayout sizes=row();box.addView(sizes,new LinearLayout.LayoutParams(-1,-2));x=cell(sizes,"X %",prefs().getInt("overlay_"+key+"_x",dx));y=cell(sizes,"Y %",prefs().getInt("overlay_"+key+"_y",dy));w=cell(sizes,tx("عرض %","Width %"),prefs().getInt("overlay_"+key+"_w",dw));h=cell(sizes,tx("ارتفاع %","Height %"),prefs().getInt("overlay_"+key+"_h",dh));alpha=cell(sizes,tx("عتامة %","Opacity %"),prefs().getInt("overlay_"+key+"_alpha",da));tools.add(this);}
        void save(SharedPreferences.Editor editor){editor.putBoolean("overlay_"+key,enabled.isChecked()).putInt("overlay_"+key+"_x",number(x,0,0,100)).putInt("overlay_"+key+"_y",number(y,0,0,100)).putInt("overlay_"+key+"_w",number(w,10,1,100)).putInt("overlay_"+key+"_h",number(h,10,1,100)).putInt("overlay_"+key+"_alpha",number(alpha,100,5,100));}
        private EditText cell(LinearLayout row,String label,int value){LinearLayout box=col();box.setPadding(dp(3),0,dp(3),0);row.addView(box,new LinearLayout.LayoutParams(0,-2,1));box.addView(text(label,13,false));EditText input=field(box,label,Integer.toString(value));numeric(input,false);return input;}
    }
}
