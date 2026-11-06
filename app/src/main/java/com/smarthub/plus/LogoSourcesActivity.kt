package com.smarthub.plus
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*

class LogoSourcesActivity:HubActivity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var state:TextView
 private lateinit var chmax:CheckBox
 private lateinit var receiving:TextView
 private var receiver:ChlImportServer?=null
 private var foreground=false
 override fun onCreate(saved:Bundle?){
  super.onCreate(saved)
  val body=page(tx("شعارات القنوات · IRAQhub","Channel logos · IRAQhub"),
    tx("جلب الشعار بالهوية الدقيقة وحفظه للقناة، بدون تغيير قنوات الرسيفر.",
      "Exact channel identity, cached logos and optional authorised CHMAX."));
  val active=SatelliteState.read(this)
  val overview=card(body)
  overview.addView(text(tx("القناة الحالية","Current receiver channel"),20,true))
  val currentName=active.channelName.ifBlank{tx("لا توجد قناة محددة","No channel selected")}
  val display=text(currentName,24,true)
  display.setTextColor(0xffffd36c.toInt())
  overview.addView(display)
  if(active.channelId.isNotBlank()){
   val reference=text(active.channelId,12,false);overview.addView(reference)
  }
  val panel=card(body)
  panel.addView(text(tx("اختبار الشعارات","Logo status and search"),20,true))
  state=text(tx("قراءة حالة المصادر…","Loading source status…"),15,false)
  panel.addView(state)
  action(panel,tx("جلب شعار القناة من CHMAX أو مصدر مستقل","Find current channel logo"),Runnable{testCurrent()})
  action(panel,tx("اختبار تنزيل CHMAX الأصلي المصرّح","Test authorised CHMAX icon download"),Runnable{testChmax()})
  action(panel,tx("ربط شعار PNG/JPEG من USB بالقناة الحالية","Assign PNG/JPEG from USB"),Runnable{open(4)})
  action(panel,tx("ربط هوية الرسيفر بخدمة CHMAX الأصلية","Connect authorised CHMAX account"),Runnable{
   startActivity(Intent(this,FactorySettingsActivity::class.java))
  })
  val options=card(body)
  options.addView(text(tx("خدمات CHMAX","CHMAX service"),20,true))
  chmax=CheckBox(this)
  chmax.text=tx("السماح بطلب شعار CHMAX إذا توفر ملف توافق مصادق","Use CHMAX when a licensed icon profile exists")
  chmax.setTextColor(-1);chmax.isChecked=ChlLogoAddon.get(this).enabled()
  options.addView(chmax)
  chmax.setOnCheckedChangeListener{_,checked->
   ChlLogoAddon.get(this).enable(checked)
   LogoRepository.get(this).invalidate();refresh()
  }
  options.addView(text(tx("المصدر المستقل يعمل حتى لو رفض CHMAX المصادقة. ملف CHL لا يحتوي صوراً، لذا لا بد من مصدر شعار فعلي.",
   "Independent logos work without CHMAX authorisation. CHL contains channel identities, not image bytes."),14,false))
  val imports=card(body)
  imports.addView(text(tx("ملفات CHL والنسخ الاحتياطية","CHL & backups"),20,true))
  action(imports,tx("استيراد ملف قنوات CHL","Import CHL channel list"),Runnable{open(3)})
  action(imports,tx("استقبال ملف CHL من الكمبيوتر/الهاتف","Receive CHL over local network"),Runnable{startReceiver()})
  receiving=text(tx("الاستقبال المحلي متوقف","Local receiver stopped"),13,false)
  receiving.textDirection=android.view.View.TEXT_DIRECTION_LTR
  receiving.setTextIsSelectable(true)
  imports.addView(receiving)
  action(imports,tx("إيقاف استقبال الملفات","Stop local receiver"),Runnable{stopReceiver()})
  action(imports,tx("استيراد أسماء ومصادر الشعارات JSON","Import logo identity/mapping JSON"),Runnable{open(1)})
  action(imports,tx("تصدير قاعدة الشعارات","Export logo mapping"),Runnable{
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
      .setType("application/json").putExtra(Intent.EXTRA_TITLE,"iraqhub-logo-mapping.json"),2)
  })
  action(imports,tx("استعادة مصادر الشعارات الأصلية","Restore bundled logo sources"),Runnable{
   scope.launch{try{
    val json=withContext(Dispatchers.IO){
     assets.open("logo-resolver/mapping.json").bufferedReader().use{it.readText()}
    }
    LogoRepository.get(this@LogoSourcesActivity).replaceMapping(json,this@LogoSourcesActivity)
    message(tx("تمت استعادة المطابقات","Logo mapping restored"))
   }catch(_:Exception){message(tx("تعذرت استعادة القاعدة","Could not restore mapping"))}}
  })
  action(imports,tx("حذف بيانات CHL المستوردة","Clear imported CHL"),Runnable{
   android.app.AlertDialog.Builder(this)
    .setTitle(tx("إزالة ملف CHL؟","Remove CHL file?"))
    .setMessage(tx("سيتم حذف بيانات القنوات المستوردة من IRAQhub. لن تتغير قنوات المصنع.",
      "This deletes IRAQhub's imported channel data, not firmware channels."))
    .setPositiveButton(tx("حذف","Delete")){_,_->
     scope.launch{
      withContext(Dispatchers.IO){ChlLogoAddon.get(this@LogoSourcesActivity).clear()}
      chmax.isChecked=false;LogoRepository.get(this@LogoSourcesActivity).invalidate();refresh()
     }
    }.setNegativeButton(tx("إلغاء","Cancel"),null).show()
  })
  refresh()
 }
 private fun open(code:Int){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);if(intent.resolveActivity(packageManager)==null&&code==3){message("لا يوجد منتقي ملفات؛ افتح الرابط المحلي من الكمبيوتر أو الهاتف");startReceiver()}else startActivityForResult(intent,code)}
 private fun startReceiver(){stopReceiver();scope.launch{try{val pair=withContext(Dispatchers.IO){val server=ChlImportServer{input->val parsed=ChlLogoAddon.get(this@LogoSourcesActivity).importFile(input.reader(Charsets.UTF_8));LogoRepository.get(this@LogoSourcesActivity).invalidate();runOnUiThread{if(!isDestroyed)refresh()};"تم استيراد "+parsed.services.size+" قناة دون تعديل قائمة الرسيفر"};server to server.localAddress()};if(!foreground||pair.second.isEmpty()){pair.first.close();return@launch};receiver=pair.first;receiving.text="افتح الرابط من جهاز على الشبكة نفسها واختر CHL. يغلق عند مغادرة الصفحة.\n"+pair.second}catch(_:Exception){message("تعذر بدء استقبال الملف عبر الشبكة المحلية")}}}
 private fun stopReceiver(){receiver?.close();receiver=null;if(::receiving.isInitialized)receiving.text="الاستقبال المحلي متوقف"}
 private fun message(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
 private fun refresh(){scope.launch{val summary=withContext(Dispatchers.IO){val a=ChlLogoAddon.get(this@LogoSourcesActivity);val i=a.snapshot();if(i==null)"لا يوجد ملف CHL مستورد" else "قنوات CHL: "+i.services.size+"\nالأقمار: "+i.satellites+" · الترددات: "+i.transponders+"\nقنوات لها مزود: "+i.services.count{it.provider.isNotBlank()}+"\nلا يحتوي الملف صور شعارات."};state.text=summary+"\n"+ChlLogoAddon.get(this@LogoSourcesActivity).statusLabel()}}
 private fun testChmax(){
  val selected=SatelliteState.read(this)
  if(!selected.channelId.startsWith("DVB:")||selected.channelName.isBlank()){
   message(tx("اختر قناة DVB من قائمة IRAQhub أولاً","Choose an IRAQhub DVB channel first"))
   return
  }
  val addon=ChlLogoAddon.get(this)
  if(!addon.enabled()){
   message(tx("فعّل CHMAX من المفتاح الاختياري أولاً","Enable optional CHMAX source first"))
   return
  }
  if(!addon.supportedSession()){
   state.text=tx("لا توجد جلسة CHMAX مع ملف توافق صادر من المصنع. استخدم مصادر الشعارات المستقلة أو اربط حساباً مخولاً.",
     "CHMAX icon authorisation/profile unavailable. Use independent logos or connect a licensed receiver account.")
   return
  }
  state.text=tx("جارٍ فحص خدمة CHMAX للقناة: ","Checking authorised CHMAX for ")+selected.channelName
  scope.launch{
   val result=withContext(Dispatchers.IO){
    try{
     val resolver=ChannelLogoResolver(this@LogoSourcesActivity,
       ChannelAliasDatabase(this@LogoSourcesActivity))
     val bitmap=addon.resolvePhysical(selected.channelId,resolver)
     if(bitmap!=null){
      ChannelLogoCache(this@LogoSourcesActivity)
        .save("channel:physical:"+selected.channelId,bitmap)
      CHMAXIntegration.saveObservedLogo(this@LogoSourcesActivity,
        selected.channelId,selected.channelName,bitmap)
      getSharedPreferences("smarthub",0).edit()
       .putString("native_logo_source:"+CHMAXIntegration.identity(selected),"CHMAX_AUTHORISED")
       .putLong("native_logos_changed_at",System.currentTimeMillis()).apply()
     }
     bitmap!=null
    }catch(_:Exception){false}
   }
   if(isDestroyed)return@launch
   if(result){
    LogoRepository.get(this@LogoSourcesActivity).invalidate()
    state.text=tx("تم تنزيل وتخزين شعار CHMAX للقناة: ","CHMAX icon cached for ")+selected.channelName
   }else state.text=addon.statusLabel()
  }
 }
 private fun testCurrent(){
  val channel=SatelliteState.read(this)
  val name=channel.channelName
  if(name.isBlank()){message("القناة الحالية غير معروفة");return}
  scope.launch{val a=ChlLogoAddon.get(this@LogoSourcesActivity);withContext(Dispatchers.IO){a.load()};val matches=a.snapshot()?.candidates(name)?:emptyList()
   if(a.enabled()&&matches.size>1&&a.unique(name)==null)message("الاسم مكرر؛ اختر شعاراً يدوياً أو استخدم معرف DVB الصحيح")
   state.text="جارٍ البحث · "+name
   LogoRepository.get(this@LogoSourcesActivity).request(name,channel.channelId,object:LogoRepository.Listener{override fun channel(bitmap:android.graphics.Bitmap?){state.text=name+" · "+(if(bitmap==null)"لم يتوفر شعار مطابق" else "شعار جاهز "+bitmap.width+"×"+bitmap.height)+"\n"+ChlLogoAddon.get(this@LogoSourcesActivity).statusLabel()};override fun provider(name:String,bitmap:android.graphics.Bitmap?){}})
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
