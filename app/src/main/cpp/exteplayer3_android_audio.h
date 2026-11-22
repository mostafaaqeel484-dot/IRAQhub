// Experimental Android AudioTrack adapter for the ExtePlayer3 PTS / FFmpeg path.
// Audio decoding is FFmpeg software, unlike Enigma2's LinuxDVB hardware writer.
// The upstream ExtePlayer3 audio subsystem itself is NOT ported.
#pragma once
#include <jni.h>
#include <atomic>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <vector>
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

class ExtePlayer3AndroidAudio {
 struct CodecFree {
  void operator()(AVCodecContext* p)const{if(p)avcodec_free_context(&p);}
 };
 struct FrameFree {
  void operator()(AVFrame* p)const{if(p)av_frame_free(&p);}
 };
 struct ResamplerFree {
  void operator()(SwrContext* p)const{if(p)swr_free(&p);}
 };
 using Codec=std::unique_ptr<AVCodecContext,CodecFree>;
 using Frame=std::unique_ptr<AVFrame,FrameFree>;
 using Resampler=std::unique_ptr<SwrContext,ResamplerFree>;
 JNIEnv* env_;
 jobject owner_;
 AVFormatContext* input_;
 bool enabled_;
 int active_=-2;
 Codec codec_;
 Frame frame_{av_frame_alloc()};
 Resampler swr_;
 std::vector<uint8_t> pcm_{std::vector<uint8_t>(8192*4)};
 jmethodID onTrack_=nullptr,onPcm_=nullptr,onReset_=nullptr;
 void clearException(){if(env_->ExceptionCheck())env_->ExceptionClear();}
public:
 ExtePlayer3AndroidAudio(JNIEnv* env,jobject owner,AVFormatContext* input,
                          bool enable,jclass receiver):
   env_(env),owner_(owner),input_(input),enabled_(enable){
  onTrack_=env_->GetMethodID(receiver,"onNativeAudioTrack",
     "(ILjava/lang/String;Ljava/lang/String;)V");
  onPcm_=env_->GetMethodID(receiver,"onNativePcm","([BI)V");
  onReset_=env_->GetMethodID(receiver,"onNativeAudioReset","()V");
  clearException();
 }
 bool valid()const{return onTrack_&&onPcm_&&onReset_&&frame_;}
 int enumerate(){
  if(!valid()||!enabled_||!input_)return -1;
  int first=-1;
  for(unsigned i=0;i<input_->nb_streams&&i<128;++i){
   const AVStream* stream=input_->streams[i];
   if(!stream||!stream->codecpar||
      stream->codecpar->codec_type!=AVMEDIA_TYPE_AUDIO)continue;
   if(first<0)first=static_cast<int>(i);
   const AVDictionaryEntry* language=av_dict_get(stream->metadata,
      "language",nullptr,0);
   jstring lang=env_->NewStringUTF(
      language&&language->value?language->value:"und");
   jstring codecName=env_->NewStringUTF(
      avcodec_get_name(stream->codecpar->codec_id));
   if(lang&&codecName)
    env_->CallVoidMethod(owner_,onTrack_,static_cast<jint>(i),lang,codecName);
   if(lang)env_->DeleteLocalRef(lang);
   if(codecName)env_->DeleteLocalRef(codecName);
   clearException();
  }
  return first;
 }
 int active()const{return active_;}
 void select(int chosen){
  if(active_==chosen)return;
  codec_.reset();swr_.reset();active_=chosen;
  if(valid())env_->CallVoidMethod(owner_,onReset_);
  clearException();
  if(!valid()||!enabled_||chosen<0||
      chosen>=static_cast<int>(input_->nb_streams))return;
  AVStream* stream=input_->streams[chosen];
  if(!stream||!stream->codecpar||
     stream->codecpar->codec_type!=AVMEDIA_TYPE_AUDIO)return;
  const AVCodec* decoder=avcodec_find_decoder(stream->codecpar->codec_id);
  if(!decoder)return;
  Codec candidate(avcodec_alloc_context3(decoder));
  if(!candidate||
     avcodec_parameters_to_context(candidate.get(),stream->codecpar)<0)return;
  candidate->thread_count=1;
  if(avcodec_open2(candidate.get(),decoder,nullptr)<0)return;
  AVChannelLayout stereo;
  av_channel_layout_default(&stereo,2);
  SwrContext* raw=nullptr;
  int rc=swr_alloc_set_opts2(&raw,&stereo,AV_SAMPLE_FMT_S16,48000,
     &candidate->ch_layout,candidate->sample_fmt,candidate->sample_rate,
     0,nullptr);
  av_channel_layout_uninit(&stereo);
  if(rc<0||!raw){if(raw)swr_free(&raw);return;}
  Resampler resample(raw);
  if(swr_init(resample.get())<0)return;
  codec_=std::move(candidate);
  swr_=std::move(resample);
 }
 void feed(AVPacket* packet,const std::atomic<bool>& stopped){
  if(!codec_||!swr_||!frame_||!packet||stopped.load())return;
  int rc=avcodec_send_packet(codec_.get(),packet);
  if(rc==AVERROR(EAGAIN)){
   drain(stopped);
   rc=avcodec_send_packet(codec_.get(),packet);
  }
  if(rc>=0)drain(stopped);
 }
 void drain(const std::atomic<bool>& stopped){
  while(!stopped.load(std::memory_order_relaxed)){
   int rc=avcodec_receive_frame(codec_.get(),frame_.get());
   if(rc==AVERROR(EAGAIN)||rc==AVERROR_EOF||rc<0)break;
   if(frame_->nb_samples>0&&codec_->sample_rate>0){
    int wanted=static_cast<int>(av_rescale_rnd(
       swr_get_delay(swr_.get(),codec_->sample_rate)+frame_->nb_samples,
       48000,codec_->sample_rate,AV_ROUND_UP));
    if(wanted>0&&wanted<=8192){
     uint8_t* out=pcm_.data();
     int count=swr_convert(swr_.get(),&out,8192,
       const_cast<const uint8_t**>(frame_->extended_data),
       frame_->nb_samples);
     if(count>0){
      int bytes=count*4;
      jbyteArray block=env_->NewByteArray(bytes);
      if(block){
       env_->SetByteArrayRegion(block,0,bytes,
          reinterpret_cast<const jbyte*>(pcm_.data()));
       env_->CallVoidMethod(owner_,onPcm_,block,static_cast<jint>(bytes));
       env_->DeleteLocalRef(block);
       clearException();
      }
     }
    }
   }
   av_frame_unref(frame_.get());
  }
 }
};
