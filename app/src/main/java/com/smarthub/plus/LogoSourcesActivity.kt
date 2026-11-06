package com.smarthub.plus
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*

class LogoSourcesActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var state:TextView
 private lateinit var chmax:CheckBox
 private lateinit var receiving:TextView
 private var receiver:ChlImportServer?=null
 private var foreground=false
 override fun onCreate(saved:Bundle?){
  super.onCreate(saved)
  val body=LinearLayout(this);body.orientation=1;body.setPadding(36,28,36,28);body.setBackgroundColor(0xff102334.toInt())
  fun text(s:String,size:Float=18f):TextView=TextView(this).also{it.text=s;it.textSize=size;it.setTextColor(-1);body.addView(it)}
  text("IRAQhub · مصادر الشعارات وإضافات CHL",24f)
  text("الشعارات المستقلة هي المصدر الأساسي. ملفات CHL تضيف أسماء القنوات ومزوديها ومعرّفات البث، ولا تغيّر قائمة الرسيفر. لا تُعتبر بيانات الملف قياسات حية للإشارة أو الدقة.")
  fun button(label:String,run:()->Unit){val b=Button(this);b.text=label;b.setOnClickListener{try{run()}catch(_:Exception){message("تعذر فتح متصفح الملفات")}};body.addView(b)}
  button("تعيين شعار PNG/JPEG للقناة الحالية من USB"){open(4)}
  button("استيراد ملف قنوات CHL"){open(3)}
  state=text("جارٍ قراءة الإضافات…")
  button("استقبال CHL من الكمبيوتر أو الهاتف"){startReceiver()}
  receiving=text("الاستقبال المحلي متوقف");receiving.textDirection=android.view.View.TEXT_DIRECTION_LTR;receiving.setTextIsSelectable(true)
  button("إيقاف استقبال الملفات"){stopReceiver()}
  chmax=CheckBox(this);chmax.text="إضافة CHMAX الاختيارية: الشعارات عبر معرّفات CHL";chmax.setTextColor(-1);chmax.isChecked=ChlLogoAddon.get(this).enabled();body.addView(chmax)
  chmax.setOnCheckedChangeListener{_,checked->ChlLogoAddon.get(this).enable(checked);LogoRepository.get(this).invalidate();refresh()}
  text("إضافة CHMAX اختيارية وتحتاج بيانات رسيفر صحيحة. عند تكرار أسماء القنوات لا نختار شعاراً عشوائياً؛ اختر شعاراً يدوياً أو استخدم معرف DVB الدقيق.")
  button("اختبار شعار القناة الحالية"){testCurrent()}
  button("إزالة بيانات CHL المستوردة"){scope.launch{withContext(Dispatchers.IO){ChlLogoAddon.get(this@LogoSourcesActivity).clear()};chmax.isChecked=false;LogoRepository.get(this@LogoSourcesActivity).invalidate();refresh()}}
  button("استيراد قاعدة الأسماء ومصادر الصور JSON"){open(1)}
  button("تصدير قاعدة الأسماء لتعديلها"){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"iraqhub-logo-mapping.json"),2)}
  button("استعادة قاعدة الشعارات الأصلية"){scope.launch{try{val json=withContext(Dispatchers.IO){assets.open("logo-resolver/mapping.json").bufferedReader().use{it.readText()}};LogoRepository.get(this@LogoSourcesActivity).replaceMapping(json,this@LogoSourcesActivity);message("تمت استعادة المطابقات")}catch(_:Exception){message("تعذر تحديث القاعدة")}}}
  button("رجوع"){finish()}
  val scroll=ScrollView(this);scroll.addView(body);setContentView(scroll);refresh()
 }
 private fun open(code:Int){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);if(intent.resolveActivity(packageManager)==null&&code==3){message("لا يوجد منتقي ملفات؛ افتح الرابط المحلي من الكمبيوتر أو الهاتف");startReceiver()}else startActivityForResult(intent,code)}
 private fun startReceiver(){stopReceiver();scope.launch{try{val pair=withContext(Dispatchers.IO){val server=ChlImportServer{input->val parsed=ChlLogoAddon.get(this@LogoSourcesActivity).importFile(input.reader(Charsets.UTF_8));LogoRepository.get(this@LogoSourcesActivity).invalidate();runOnUiThread{if(!isDestroyed)refresh()};"تم استيراد "+parsed.services.size+" قناة دون تعديل قائمة الرسيفر"};server to server.localAddress()};if(!foreground||pair.second.isEmpty()){pair.first.close();return@launch};receiver=pair.first;receiving.text="افتح الرابط من جهاز على الشبكة نفسها واختر CHL. يغلق عند مغادرة الصفحة.\n"+pair.second}catch(_:Exception){message("تعذر بدء استقبال الملف عبر الشبكة المحلية")}}}
 private fun stopReceiver(){receiver?.close();receiver=null;if(::receiving.isInitialized)receiving.text="الاستقبال المحلي متوقف"}
 private fun message(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
 private fun refresh(){scope.launch{val summary=withContext(Dispatchers.IO){val a=ChlLogoAddon.get(this@LogoSourcesActivity);val i=a.snapshot();if(i==null)"لا يوجد ملف CHL مستورد" else "قنوات CHL: "+i.services.size+"\nالأقمار: "+i.satellites+" · الترددات: "+i.transponders+"\nقنوات لها مزود: "+i.services.count{it.provider.isNotBlank()}+"\nلا يحتوي الملف صور شعارات."};state.text=summary+"\n"+ChlLogoAddon.get(this@LogoSourcesActivity).statusLabel()}}
 private fun testCurrent(){
  val name=SatelliteState.read(this).channelName
  if(name.isBlank()){message("القناة الحالية غير معروفة");return}
  scope.launch{val a=ChlLogoAddon.get(this@LogoSourcesActivity);withContext(Dispatchers.IO){a.load()};val matches=a.snapshot()?.candidates(name)?:emptyList()
   if(a.enabled()&&matches.size>1&&a.unique(name)==null)message("الاسم مكرر؛ اختر شعاراً يدوياً أو استخدم معرف DVB الصحيح")
   state.text="جارٍ البحث · "+name
   LogoRepository.get(this@LogoSourcesActivity).request(name,object:LogoRepository.Listener{override fun channel(bitmap:android.graphics.Bitmap?){state.text=name+" · "+(if(bitmap==null)"لم يتوفر شعار مطابق" else "شعار جاهز "+bitmap.width+"×"+bitmap.height)+"\n"+ChlLogoAddon.get(this@LogoSourcesActivity).statusLabel()};override fun provider(name:String,bitmap:android.graphics.Bitmap?){}})
  }
 }
 override fun onActivityResult(request:Int,result:Int,data:Intent?){
  super.onActivityResult(request,result,data);val uri=data?.data?:return;if(result!=RESULT_OK)return
  scope.launch{try{
   when(request){
    4->{
      val channel=SatelliteState.read(this@LogoSourcesActivity)
      require(channel.channelId.isNotBlank()&&channel.channelName.isNotBlank()){"NO_ACTIVE_CHANNEL"}
      withContext(Dispatchers.IO){
       val bytes=contentResolver.openInputStream(uri)!!.use { stream->
        val output=java.io.ByteArrayOutputStream()
        val block=ByteArray(8192)
        while(true){val count=stream.read(block);if(count<0)break
         require(output.size()+count<=4*1024*1024){"IMAGE_TOO_LARGE"}
         output.write(block,0,count)
        }
        output.toByteArray()
       }
       val probe=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
       android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,probe)
       require(probe.outWidth in 32..4096&&probe.outHeight in 24..4096){"INVALID_IMAGE"}
       val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
         ?:throw java.io.IOException("IMAGE_DECODE_FAILED")
       try{CHMAXIntegration.saveObservedLogo(this@LogoSourcesActivity,channel.channelId,channel.channelName,bitmap)}
       finally{bitmap.recycle()}
       getSharedPreferences("smarthub",0).edit()
         .putString("native_logo_source:"+CHMAXIntegration.identity(channel),"MANUAL_CHANNEL_IMAGE")
         .putLong("native_logos_changed_at",System.currentTimeMillis()).apply()
      }
      LogoRepository.get(this@LogoSourcesActivity).invalidate()
      message("حُفظ شعار القناة المحددة داخل IRAQhub")
      refresh()
    }
    3->{val index=withContext(Dispatchers.IO){contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use{ChlLogoAddon.get(this@LogoSourcesActivity).importFile(it)}};LogoRepository.get(this@LogoSourcesActivity).invalidate();message("تم استيراد "+index.services.size+" قناة دون تعديل الرسيفر");refresh()}
    1->{val json=withContext(Dispatchers.IO){val out=java.io.ByteArrayOutputStream();contentResolver.openInputStream(uri)!!.use{val buf=ByteArray(8192);while(true){val n=it.read(buf);if(n<0)break;require(out.size()+n<=2*1024*1024);out.write(buf,0,n)}};out.toString("UTF-8")};LogoRepository.get(this@LogoSourcesActivity).replaceMapping(json,this@LogoSourcesActivity);message("تم التحقق من المطابقات وحفظها")}
    2->{withContext(Dispatchers.IO){val file=java.io.File(filesDir,"logo-mapping.json");val bytes=if(file.exists())file.readBytes()else assets.open("logo-resolver/mapping.json").use{it.readBytes()};contentResolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)}};message("تم تصدير القاعدة")}
   }
  }catch(_:Exception){message(if(request==3)"لم يُقبل CHL: تنسيق غير مدعوم أو مرجع تردد غير صالح. بقيت البيانات السابقة." else "لم يُقبل الملف: تحقق من JSON والمطابقات وروابط HTTPS")}}
 }
 override fun onStart(){super.onStart();foreground=true}
 override fun onStop(){foreground=false;stopReceiver();super.onStop()}
 override fun onDestroy(){stopReceiver();scope.cancel();super.onDestroy()}
}
