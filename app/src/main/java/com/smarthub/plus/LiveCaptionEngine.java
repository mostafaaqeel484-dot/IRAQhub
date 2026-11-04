package com.smarthub.plus;
import android.content.*;
import android.graphics.Bitmap;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.*;
import com.google.mlkit.nl.translate.*;
import com.google.mlkit.common.model.DownloadConditions;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Single-worker, local OCR + translation. No frames/text go to a translation server. */
final class LiveCaptionEngine implements AutoCloseable {
 private final Context context;private final LanguageIdentifier identifier=LanguageIdentification.getClient();private TessBaseAPI tess;private Translator translator;private String pair="";private boolean modelReady;
 private final LinkedHashMap<String,String> cache=new LinkedHashMap<String,String>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>256;}};
 LiveCaptionEngine(Context c){context=c.getApplicationContext();}
 private void status(String message){context.getSharedPreferences("smarthub",0).edit().putString("live_translation_status",message).apply();}
 synchronized void prepareOcr()throws Exception{if(tess!=null)return;status("تنزيل / تجهيز نماذج OCR العربية والإنجليزية…");File dir=new File(context.getFilesDir(),"tesseract/tessdata");dir.mkdirs();for(String language:new String[]{"eng","ara"}){File file=new File(dir,language+".traineddata");if(file.length()<100000){byte[] bytes=DirectHttp.request("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/"+language+".traineddata",Collections.emptyMap(),null,18*1024*1024,null).bytes;if(bytes.length<100000)throw new IOException("OCR model download incomplete");File temp=new File(dir,language+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}if(!temp.renameTo(file))throw new IOException("Cannot save OCR model");}}TessBaseAPI api=new TessBaseAPI();if(!api.init(dir.getParent(),"eng+ara",TessBaseAPI.OEM_LSTM_ONLY)){api.recycle();throw new IOException("OCR model initialization failed");}api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);tess=api;status("OCR جاهز · المعالجة محلية");}
 synchronized String recognize(Bitmap image)throws Exception{prepareOcr();Bitmap scaled=image.getWidth()>1280?Bitmap.createScaledBitmap(image,1280,Math.max(1,image.getHeight()*1280/image.getWidth()),true):image;try{tess.setImage(scaled);String value=tess.getUTF8Text();int confidence=tess.meanConfidence();tess.clear();return confidence<35||value==null?"":value.trim().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","");}finally{if(scaled!=image)scaled.recycle();}}
 synchronized String translate(String text,String hint)throws Exception{String target=context.getSharedPreferences("smarthub",0).getString("live_target_language","ar");if(text.trim().isEmpty()||target.equals("ar")&&SubtitleSyncRepository.isArabic(text))return text;String key=target+"|"+(hint==null?"":hint)+"|"+text;if(cache.containsKey(key))return cache.get(key);String source=hint==null?"":TranslateLanguage.fromLanguageTag(hint);if(source==null||source.isEmpty())source=Tasks.await(identifier.identifyLanguage(text),8,TimeUnit.SECONDS);source=TranslateLanguage.fromLanguageTag(source);if(source==null){status("تعذر تحديد لغة الجملة؛ بقي النص الأصلي");return "";}if(source.equals(target))return text;String wanted=source+"|"+target;if(!wanted.equals(pair)){if(translator!=null)translator.close();translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());pair=wanted;modelReady=false;}if(!modelReady){status("تجهيز نموذج الترجمة المحلية "+wanted+"…");Tasks.await(translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()),180,TimeUnit.SECONDS);modelReady=true;}String translated=Tasks.await(translator.translate(text),10,TimeUnit.SECONDS);cache.put(key,translated);status("ترجمة محلية · "+wanted+" · توقيت المسار محفوظ");return translated;}
 @Override public synchronized void close(){identifier.close();if(translator!=null)translator.close();if(tess!=null)tess.recycle();translator=null;tess=null;cache.clear();}
}
