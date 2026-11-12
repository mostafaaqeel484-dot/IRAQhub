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
  action(panel,tx("إصلاح 12 · مصادر لوغوات الرياضة وبوسترات المسلسلات وWikimedia",
    "Fix 12 · sports logos, TVmaze posters and licensed Commons"),Runnable{
    startActivity(Intent(this,ArtworkSourcesActivity::class.java))
  })
  val options=card(body)
  options.addView(text(tx("مصادر شعارات القنوات — مفاتيح مستقلة","Channel logo sources — independent switches"),20,true))
  chmax=CheckBox(this)
  chmax.text=tx("شعارات القنوات من CHMAX / المصنع","Channel logos from CHMAX / receiver")
  chmax.setTextColor(-1);chmax.isChecked=ChannelLogoSourcePrefs.chmax(this)
  options.addView(chmax)
  chmax.setOnCheckedChangeListener{_,enabled->
   ChannelLogoSourcePrefs.set(this,ChannelLogoSourcePrefs.CHMAX,enabled)
   if(enabled&&!ChlLogoAddon.get(this).enabled())ChlLogoAddon.get(this).enable(true)
   refresh()
  }
  val external=CheckBox(this)
  external.text=tx("شعارات القنوات من الإنترنت والملفات المحلية","External / local channel logos")
  external.setTextColor(-1);external.isChecked=ChannelLogoSourcePrefs.external(this)
  options.addView(external)
  external.setOnCheckedChangeListener{_,enabled->
   ChannelLogoSourcePrefs.set(this,ChannelLogoSourcePrefs.EXTERNAL,enabled)
   refresh()
  }
  options.addView(text(tx("يمكن تشغيل CHMAX والخارجي سويةً؛ CHMAX أولاً، وعند غياب الشعار يُستخدم الخارجي. إيقاف أحدهما يمنع إعادة استعمال كاشه القديم.",
    "Enable both: CHMAX first, then independent sources for missing logos. Disabled sources cannot reuse old artwork."),13,false))
  val providerSwitch=CheckBox(this)
  providerSwitch.text=tx("إظهار شعارات المزوّدين (مستقل عن المصدرين أعلاه)",
    "Provider logos (independent of both channel sources)")
  providerSwitch.setTextColor(-1)
  providerSwitch.isChecked=ChannelLogoSourcePrefs.providers(this)
  options.addView(providerSwitch)
  providerSwitch.setOnCheckedChangeListener{_,enabled->
   ChannelLogoSourcePrefs.set(this,ChannelLogoSourcePrefs.PROVIDERS,enabled)
  }
  action(options,tx("إضافة مزوّد وشعاره يدوياً","Add a provider and its logo"),Runnable{
   startActivity(Intent(this,ProviderLogoEditorActivity::class.java))
  })
  action(options,tx("عرض شعارات المزوّدين الـ234","Browse the 234 provider brands"),Runnable{
   startActivity(Intent(this,ProviderLogoCatalogActivity::class.java))
  })
  options.addView(text(tx("إضافة شعار مزوّد لا تلمس CHMAX أو الكاش الخارجي الخاص بالقنوات.",
   "Provider logos are stored separately from CHMAX and external channel images."),13,false))
  val imports=card(body)
  imports.addView(text(tx("ملفات القنوات · SQLite DB وCHL","Channel lists · SQLite DB & CHL"),20,true))
  action(imports,tx("استيراد قاعدة قنوات SQLite .db (سريع)","Fast SQLite .db channel import"),Runnable{open(5)})
  action(imports,tx("دمج DB من Aurum بدون حذف القنوات الحالية","Merge exported Aurum DB (preserve channels)"),Runnable{open(7)})
  action(imports,tx("تصدير قاعدة قنوات SQLite .db","Export SQLite .db channel backup"),Runnable{
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
      .setType("application/octet-stream")
      .putExtra(Intent.EXTRA_TITLE,"IRAQhub_channels.db"),6)
  })
  action(imports,tx("إصلاح · استيراد لوغوات من حزمة USB ADB ZIP بهويات DVB",
    "Fix · Import USB ADB logo ZIP with exact DVB IDs"),Runnable{open(8)})
  action(imports,tx("استيراد ملف قنوات CHL","Import CHL channel list"),Runnable{open(3)})
  imports.addView(text(tx("الاستيراد الكامل يستبدل قائمة IRAQhub فقط. خيار الدمج الجديد يقرأ قاعدة Aurum المصدّرة، يفحصها، ويضيف القنوات غير الموجودة حسب القمر/التردد/SID مع حفظ ترتيب ومفضلة القنوات الحالية. احفظ نسخة احتياطية أولاً.",
    "Full import replaces only the IRAQhub list. Merge validates an exported Aurum DB and adds missing satellite/TP/SID identities while preserving existing order and favourites. Back up first."),13,false))
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
  action(imports,tx("حذف قائمة IRAQhub المحلية · CHL/DB","Clear IRAQhub local CHL/DB catalogue"),Runnable{
   android.app.AlertDialog.Builder(this)
    .setTitle(tx("حذف قاعدة القنوات المحلية؟","Remove local channel database?"))
    .setMessage(tx("سيتم حذف قنوات IRAQhub المستوردة من CHL أو DB والمفضلة. لن تتغير قنوات Aurum الأصلية.",
      "Deletes IRAQhub local CHL/DB channels and favourites, not firmware channels."))
    .setPositiveButton(tx("حذف","Delete")){_,_->
     scope.launch{
      withContext(Dispatchers.IO){ChlLogoAddon.get(this@LogoSourcesActivity).clear()}
      chmax.isChecked=false;LogoRepository.get(this@LogoSourcesActivity).invalidate();refresh()
     }
    }.setNegativeButton(tx("إلغاء","Cancel"),null).show()
  })
  refresh()
 }
 private fun open(code:Int){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);if(intent.resolveActivity(packageManager)==null&&(code==3||code==5)){message("لا يوجد منتقي ملفات؛ اختر الملف عبر منتقي Android");if(code==3)startReceiver()}else startActivityForResult(intent,code)}
 private fun startReceiver(){stopReceiver();scope.launch{try{val pair=withContext(Dispatchers.IO){val server=ChlImportServer{input->val parsed=ChlLogoAddon.get(this@LogoSourcesActivity).importFile(input.reader(Charsets.UTF_8));LogoRepository.get(this@LogoSourcesActivity).invalidate();runOnUiThread{if(!isDestroyed)refresh()};"تم استيراد "+parsed.services.size+" قناة دون تعديل قائمة الرسيفر"};server to server.localAddress()};if(!foreground||pair.second.isEmpty()){pair.first.close();return@launch};receiver=pair.first;receiving.text="افتح الرابط من جهاز على الشبكة نفسها واختر CHL. يغلق عند مغادرة الصفحة.\n"+pair.second}catch(_:Exception){message("تعذر بدء استقبال الملف عبر الشبكة المحلية")}}}
 private fun stopReceiver(){receiver?.close();receiver=null;if(::receiving.isInitialized)receiving.text="الاستقبال المحلي متوقف"}
 private fun message(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
 private fun refresh(){
  scope.launch{
   val summary=withContext(Dispatchers.IO){
    val addon=ChlLogoAddon.get(this@LogoSourcesActivity)
    val index=addon.snapshot()
    if(index==null)tx("لا يوجد فهرس قنوات مستورد","No imported channel index")
    else{
     val native=(getSharedPreferences("smarthub",0)
       .getString("receiver_catalog_format","") ?: "").startsWith("SQLITE_")
     val header=if(native)"SQLite DB" else "CHL"
     header+" · "+tx("القنوات: ","Channels: ")+index.services.size+
       "\n"+tx("الأقمار: ","Satellites: ")+index.satellites+
       " · "+tx("الترددات: ","Transponders: ")+index.transponders+
       "\n"+tx("مزودو الخدمة: ","Providers: ")+
       index.services.count{it.provider.isNotBlank()}
    }
   }
   state.text=summary+"\n"+ChlLogoAddon.get(this@LogoSourcesActivity).statusLabel()
  }
 }
 private fun testChmax(){
  val selected=SatelliteState.read(this)
  if(!selected.channelId.startsWith("DVB:")||selected.channelName.isBlank()){
   message(tx("اختر قناة DVB من قائمة IRAQhub أولاً","Choose an IRAQhub DVB channel first"))
   return
  }
  val addon=ChlLogoAddon.get(this)
  if(!ChannelLogoSourcePrefs.chmax(this)||!addon.enabled()){
   message(tx("فعّل مصدر CHMAX أولاً","Enable CHMAX channel-logo source first"))
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
        .save("channel:physical:v2:"+selected.channelId,bitmap)
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
  if(request==8){
   scope.launch{
    try{
     state.text=tx("فحص حزمة لوغوات ADB وهويات DVB…","Validating ADB logo ZIP and DVB identities…")
     val summary=withContext(Dispatchers.IO){
      contentResolver.openInputStream(uri)!!.use{
       AdbLogoBundleImporter.importUserSelected(this@LogoSourcesActivity,it)
      }
     }
     if(isDestroyed)return@launch
     state.text=tx("تم استيراد ","Imported ")+summary.imported+
       tx(" شعار؛ غير موجودة: "," logos; missing: ")+summary.missing+
       tx("؛ غير صالحة: ","; invalid: ")+summary.invalid
     refresh()
    }catch(e:Exception){
     state.text=tx("حزمة USB ADB غير صالحة: ","USB ADB logo ZIP invalid: ")+(e.message?:"INVALID_ZIP")
    }
   }
   return
  }
  if(request==7){
   scope.launch{
    try{
     state.text=tx("فحص قاعدة Aurum قبل الدمج…","Checking Aurum DB before merge…")
     val summary=withContext(Dispatchers.IO){
      contentResolver.openInputStream(uri)!!.use {
       ReceiverOemSqliteMerge.preview(this@LogoSourcesActivity,it)
      }
     }
     if(isDestroyed)return@launch
     android.app.AlertDialog.Builder(this@LogoSourcesActivity)
      .setTitle(tx("معاينة دمج قاعدة Aurum","Preview Aurum DB merge"))
      .setMessage(summary.label()+"\\n"+tx(
       "ستبقى القنوات الحالية وأرقامها وترتيبها ومفضلتها. تضاف الخدمات غير الموجودة فقط، ويُدمج انتماء المفضلة المتحقق من الهوية. قاعدة المصنع لا تتغير. احفظ نسخة احتياطية قبل الدمج.",
       "Preserves current channels, order and favourites. Adds only missing identities and safely merges memberships. OEM database is never modified. Back up first."))
      .setPositiveButton(tx("ادمج","Merge")){_,_->
       scope.launch{
        try{
         state.text=tx("جارٍ دمج القنوات…","Merging verified services…")
         val report=withContext(Dispatchers.IO){
          contentResolver.openInputStream(uri)!!.use{
           ReceiverOemSqliteMerge.merge(this@LogoSourcesActivity,it)
          }
         }
         ChlLogoAddon.get(this@LogoSourcesActivity).databaseImported()
         LogoRepository.get(this@LogoSourcesActivity).invalidate()
         state.text=report.label()
         refresh()
        }catch(problem:Exception){
         state.text=tx("فشل دمج DB؛ التغييرات غير المكتملة أُلغيت: ",
          "DB merge failed; incomplete transaction rolled back: ")+
          (problem.message?:"DB_MERGE_FAILED")
        }
       }
      }
      .setNegativeButton(tx("إلغاء","Cancel"),null).show()
    }catch(problem:Exception){
     state.text=tx("ملف DB غير متوافق أو تالف: ","Unsupported or corrupt DB: ")+
      (problem.message?:"DB_PREVIEW_FAILED")
    }
   }
   return
  }
  if(request==5){
   android.app.AlertDialog.Builder(this)
    .setTitle(tx("استيراد قاعدة SQLite DB؟","Import SQLite DB?"))
    .setMessage(tx("سيتم استبدال قنوات IRAQhub ومفضلتها بالقنوات الموجودة في ملف DB. قاعدة قنوات Aurum لا تتغير. هل أكملت النسخ الاحتياطي؟",
      "Imports an exported Aurum or IRAQhub SQLite backup into the local catalogue. Does not modify the OEM DB. Backed up?"))
    .setPositiveButton(tx("استيراد","Import")){_,_->
     scope.launch{
      try{
       state.text=tx("جارٍ فحص SQLite واستيراد القنوات…","Validating and importing SQLite DB…")
       val total=withContext(Dispatchers.IO){
        val count=contentResolver.openInputStream(uri)!!.use{
         ReceiverSqliteImport.importStream(this@LogoSourcesActivity,it)
        }
        ChlLogoAddon.get(this@LogoSourcesActivity).databaseImported()
        count
       }
       LogoRepository.get(this@LogoSourcesActivity).invalidate()
       message(tx("تم استيراد ","Imported ")+total+tx(" قناة من DB"," channels from DB"))
       refresh()
      }catch(failure:Exception){
       val reason=failure.message?:"DB_IMPORT_FAILED"
       state.text=tx("لم يُقبل DB؛ بقيت قاعدة IRAQhub السابقة: ",
         "DB rejected; previous IRAQhub catalogue preserved: ")+reason
       message(tx("فشل استيراد DB. راجع رسالة الخطأ","SQLite DB import failed. Check error."))
      }
     }
    }.setNegativeButton(tx("إلغاء","Cancel"),null).show()
   return
  }
  scope.launch{try{
   when(request){
    6->{
      val bytes=withContext(Dispatchers.IO){
       contentResolver.openOutputStream(uri,"wt")!!.use{output->
        ReceiverSqliteImport.exportStream(this@LogoSourcesActivity,output)
       }
      }
      message(tx("تم تصدير DB بحجم ","DB exported · ")+
       (bytes/1024)+tx(" KB"," KB"))
    }
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
  }catch(problem:Exception){val reason=problem.message?:"UNAVAILABLE";state.text=reason;message(when(request){3->"لم يُقبل CHL: تنسيق غير مدعوم أو مرجع تردد غير صالح.";6->"تعذر تصدير SQLite DB: "+reason;else->"لم يُقبل الملف: تحقق من المحتوى والمطابقات."})}}
 }
 override fun onStart(){super.onStart();foreground=true}
 override fun onStop(){foreground=false;stopReceiver();super.onStop()}
 override fun onDestroy(){stopReceiver();scope.cancel();super.onDestroy()}
}
