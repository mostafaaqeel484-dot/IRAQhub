// IRAQhub Native422: direct FFmpeg avformat/avcodec + Android ANativeWindow.
// Only bundled into an explicit native422 NDK test build. LGPL FFmpeg
// build/attribution and ABI checks are mandatory before a public release.
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <atomic>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <memory>
#include <vector>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"IRAQhubNative422",__VA_ARGS__)

namespace {
struct Session {
 std::atomic<bool> stopped{false};
 std::atomic<long long> displayed{0};
 std::atomic<long long> decoded{0};
};
int interrupt(void* opaque) {
 Session* s=reinterpret_cast<Session*>(opaque);
 return s&&s->stopped.load(std::memory_order_relaxed)?1:0;
}
struct FormatCleanup {
 void operator()(AVFormatContext* ptr)const {
  if(ptr){AVFormatContext* p=ptr;avformat_close_input(&p);}
 }
};
struct CodecCleanup {
 void operator()(AVCodecContext* ptr)const{if(ptr){AVCodecContext* p=ptr;avcodec_free_context(&p);}}
};
struct PacketCleanup {
 void operator()(AVPacket* ptr)const{if(ptr){AVPacket* p=ptr;av_packet_free(&p);}}
};
struct FrameCleanup {
 void operator()(AVFrame* ptr)const{if(ptr){AVFrame* p=ptr;av_frame_free(&p);}}
};
struct WindowCleanup {
 void operator()(ANativeWindow* ptr)const{if(ptr)ANativeWindow_release(ptr);}
};
struct SwsCleanup {
 void operator()(SwsContext* ptr)const{if(ptr)sws_freeContext(ptr);}
};
struct Output {
 int width=0,height=0,sourceWidth=0,sourceHeight=0;
 AVPixelFormat sourceFormat=AV_PIX_FMT_NONE;
 SwsContext* to420=nullptr;
 SwsContext* toRgba=nullptr;
 std::vector<uint8_t> intermediate;
 std::vector<uint8_t> rgba;
 uint8_t* yuvData[4]={};
 int yuvStride[4]={};
 uint8_t* rgbaData[4]={};
 int rgbaStride[4]={};
 ~Output(){if(to420)sws_freeContext(to420);if(toRgba)sws_freeContext(toRgba);}
 bool configure(const AVFrame* source,int requestedW,int requestedH,bool convert420,
                ANativeWindow* window){
  if(!source||source->width<=0||source->height<=0||
     source->width>4096||source->height>2304)return false;
  int w=requestedW>0?requestedW:source->width;
  int h=requestedH>0?requestedH:source->height;
  // Never allocate an unlimited Surface if a malformed header claims 8K.
  if(w<=0||h<=0||w>1920||h>1080||w%2||h%2)return false;
  if(w==width&&h==height&&source->width==sourceWidth&&
      source->height==sourceHeight&&source->format==sourceFormat)return true;
  width=w;height=h;sourceWidth=source->width;sourceHeight=source->height;
  sourceFormat=static_cast<AVPixelFormat>(source->format);
  if(ANativeWindow_setBuffersGeometry(window,w,h,WINDOW_FORMAT_RGBA_8888)<0)
   return false;
  if(to420){sws_freeContext(to420);to420=nullptr;}
  if(toRgba){sws_freeContext(toRgba);toRgba=nullptr;}
  if(convert420){
   int bytes=av_image_get_buffer_size(AV_PIX_FMT_YUV420P,w,h,1);
   if(bytes<=0||bytes>24*1024*1024)return false;
   intermediate.resize(static_cast<size_t>(bytes));
   if(av_image_fill_arrays(yuvData,yuvStride,intermediate.data(),
       AV_PIX_FMT_YUV420P,w,h,1)<0)return false;
   to420=sws_getContext(source->width,source->height,sourceFormat,w,h,
     AV_PIX_FMT_YUV420P,SWS_FAST_BILINEAR,nullptr,nullptr,nullptr);
   if(!to420)return false;
  }else intermediate.clear();
  int bytes=av_image_get_buffer_size(AV_PIX_FMT_RGBA,w,h,1);
  if(bytes<=0||bytes>24*1024*1024)return false;
  rgba.resize(static_cast<size_t>(bytes));
  if(av_image_fill_arrays(rgbaData,rgbaStride,rgba.data(),AV_PIX_FMT_RGBA,
        w,h,1)<0)return false;
  toRgba=sws_getContext(convert420?w:source->width,convert420?h:source->height,
    convert420?AV_PIX_FMT_YUV420P:sourceFormat,
    w,h,AV_PIX_FMT_RGBA,SWS_FAST_BILINEAR,nullptr,nullptr,nullptr);
  return toRgba!=nullptr;
 }
 bool present(const AVFrame* frame,bool convert420,ANativeWindow* window){
  if(!frame||!configure(frame,width,height,convert420,window))return false;
  if(convert420){
   int rows=sws_scale(to420,frame->data,frame->linesize,0,frame->height,
                      yuvData,yuvStride);
   if(rows!=height)return false;
   rows=sws_scale(toRgba,(const uint8_t* const*)yuvData,yuvStride,
                  0,height,rgbaData,rgbaStride);
   if(rows!=height)return false;
  }else if(sws_scale(toRgba,frame->data,frame->linesize,0,frame->height,
                    rgbaData,rgbaStride)!=height)return false;
  ANativeWindow_Buffer surface={};
  if(ANativeWindow_lock(window,&surface,nullptr)!=0)return false;
  if(surface.format!=WINDOW_FORMAT_RGBA_8888||surface.width<width||
     surface.height<height||surface.stride<width||surface.bits==nullptr){
   ANativeWindow_unlockAndPost(window);
   return false;
  }
  auto* dest=static_cast<uint8_t*>(surface.bits);
  const size_t srcStride=static_cast<size_t>(rgbaStride[0]);
  const size_t dstStride=static_cast<size_t>(surface.stride)*4;
  const size_t copy=static_cast<size_t>(width)*4;
  for(int y=0;y<height;y++)
   memcpy(dest+static_cast<size_t>(y)*dstStride,
          rgba.data()+static_cast<size_t>(y)*srcStride,copy);
  return ANativeWindow_unlockAndPost(window)==0;
 }
};
// Source is a private LiveTsRelay URI, never an arbitrary local file path.
bool privateTs(const char* url){
 if(!url)return false;
 const char* prefix="http://127.0.0.1:";
 const size_t len=strlen(prefix);
 if(strncmp(url,prefix,len)!=0||strlen(url)>512)return false;
 const char* port=url+len;
 int count=0;
 while(*port>='0'&&*port<='9'&&count<5){port++;count++;}
 if(count==0||*port!='/')return false;
 return strlen(port)>4&&strstr(port,".ts")!=nullptr;
}
int decode(JNIEnv* env,jobject self,Session* session,const char* url,
          ANativeWindow* window,int outWidth,int outHeight,bool use420){
 if(!privateTs(url)||!window||!session)return -10;
 avformat_network_init();
 AVFormatContext* opened=avformat_alloc_context();
 if(!opened)return -11;
 opened->interrupt_callback.callback=interrupt;
 opened->interrupt_callback.opaque=session;
 AVDictionary* options=nullptr;
 av_dict_set(&options,"rw_timeout","3000000",0);
 av_dict_set(&options,"probesize","524288",0);
 av_dict_set(&options,"analyzeduration","1000000",0);
 int ret=avformat_open_input(&opened,url,nullptr,&options);
 av_dict_free(&options);
 if(ret<0){
  if(opened)avformat_free_context(opened);
  LOGE("avformat_open_input failed %d",ret);
  return -12;
 }
 std::unique_ptr<AVFormatContext,FormatCleanup> format(opened);
 if(avformat_find_stream_info(opened,nullptr)<0)return -13;
 int index=av_find_best_stream(opened,AVMEDIA_TYPE_VIDEO,-1,-1,nullptr,0);
 if(index<0)return -14;
 AVStream* stream=opened->streams[index];
 if(!stream||!stream->codecpar)return -14;
 const AVCodec* decoder=avcodec_find_decoder(stream->codecpar->codec_id);
 if(!decoder)return -15;
 std::unique_ptr<AVCodecContext,CodecCleanup> codec(avcodec_alloc_context3(decoder));
 if(!codec)return -16;
 if(avcodec_parameters_to_context(codec.get(),stream->codecpar)<0)return -16;
 codec->thread_count=2;
 codec->thread_type=FF_THREAD_FRAME;
 if(avcodec_open2(codec.get(),decoder,nullptr)<0)return -17;
 std::unique_ptr<AVPacket,PacketCleanup> packet(av_packet_alloc());
 std::unique_ptr<AVFrame,FrameCleanup> frame(av_frame_alloc());
 if(!packet||!frame)return -18;
 std::unique_ptr<Output> output(new Output());
 output->width=outWidth;output->height=outHeight;
 jclass klass=env->GetObjectClass(self);
 if(!klass)return -19;
 jmethodID onFrame=env->GetMethodID(klass,"onNativeFrame","(I)V");
 if(!onFrame){env->DeleteLocalRef(klass);return -19;}
 auto begin=std::chrono::steady_clock::now();
 int idleErrors=0;
 while(!session->stopped.load(std::memory_order_relaxed)){
  ret=av_read_frame(opened,packet.get());
  if(ret<0){
   if(ret==AVERROR(EAGAIN)&&++idleErrors<=20)continue;
   break;
  }
  idleErrors=0;
  if(packet->stream_index==index){
   int pushed=avcodec_send_packet(codec.get(),packet.get());
   if(pushed>=0||pushed==AVERROR(EAGAIN)){
    while(!session->stopped.load(std::memory_order_relaxed)){
     int received=avcodec_receive_frame(codec.get(),frame.get());
     if(received==AVERROR(EAGAIN)||received==AVERROR_EOF)break;
     if(received<0)break;
     session->decoded.fetch_add(1,std::memory_order_relaxed);
     if(output->present(frame.get(),use420,window)){
      auto shown=session->displayed.fetch_add(1,std::memory_order_relaxed)+1;
      if(shown==1||shown%60==0){
       env->CallVoidMethod(self,onFrame,static_cast<jint>(shown));
       if(env->ExceptionCheck())env->ExceptionClear();
      }
     }
     av_frame_unref(frame.get());
    }
   }
  }
  av_packet_unref(packet.get());
  if(std::chrono::steady_clock::now()-begin>std::chrono::hours(8))break;
 }
 env->DeleteLocalRef(klass);
 return session->stopped?0:session->displayed>0?0:-20;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_smarthub_plus_Native422Decoder_nativeCreate(JNIEnv*,jclass){
 return reinterpret_cast<jlong>(new(std::nothrow) Session());
}
extern "C" JNIEXPORT jint JNICALL
Java_com_smarthub_plus_Native422Decoder_nativeRun(JNIEnv* env,jclass,jlong id,
    jstring source,jobject surface,jint width,jint height,jboolean convert420){
 Session* session=reinterpret_cast<Session*>(id);
 if(!session||!source||!surface)return -1;
 const char* url=env->GetStringUTFChars(source,nullptr);
 if(!url)return -2;
 std::unique_ptr<ANativeWindow,WindowCleanup> window(ANativeWindow_fromSurface(env,surface));
 int result=decode(env,nullptr,session,url,window.get(),width,height,convert420);
 env->ReleaseStringUTFChars(source,url);
 return result;
}
extern "C" JNIEXPORT void JNICALL
Java_com_smarthub_plus_Native422Decoder_nativeStop(JNIEnv*,jclass,jlong id){
 Session* s=reinterpret_cast<Session*>(id);
 if(s)s->stopped.store(true,std::memory_order_release);
}
extern "C" JNIEXPORT void JNICALL
Java_com_smarthub_plus_Native422Decoder_nativeDestroy(JNIEnv*,jclass,jlong id){
 delete reinterpret_cast<Session*>(id);
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_smarthub_plus_Native422Decoder_nativeFrames(JNIEnv*,jclass,jlong id){
 Session* s=reinterpret_cast<Session*>(id);
 return s?s->displayed.load(std::memory_order_relaxed):0;
}
