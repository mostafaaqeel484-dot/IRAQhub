// ExtePlayer3-derived 90 kHz MPEG-TS timing bridge for Android.
// Upstream: https://github.com/skyjet18/exteplayer3
// GPL-2.0-or-later applies to the upstream pts_clock.h included below.
// This is NOT the original LinuxDVB video output or complete E2 player.
#pragma once
#include <atomic>
#include <chrono>
#include <cstdint>
#include <thread>
#include "vendor/exteplayer3/pts_clock.h"

struct ExtePlayer3AndroidClock {
 PtsClock pts{};
 int64_t firstMapped=PTS_CLOCK_INVALID;
 int64_t anchorUs=0;
 int64_t lastMapped=PTS_CLOCK_INVALID;

 static int64_t nowUs(){
  return std::chrono::duration_cast<std::chrono::microseconds>(
   std::chrono::steady_clock::now().time_since_epoch()).count();
 }
 // Called by the renderer thread for each correctly decoded video picture.
 // Source PTS is in 90 kHz ticks, wrapping at 2^33 on long MPEG-TS feeds.
 // The ExtePlayer3 PTS clock corrects wrap and discontinuities.
 // Never turn a missing PTS into an invented A/V sync measurement.
 bool paceVideo(const void* stream,int64_t raw90k,
                const std::atomic<bool>& stopped,int64_t* lateUs=nullptr){
  if(lateUs)*lateUs=0;
  if(raw90k==PTS_CLOCK_INVALID)return true;
  const int64_t now=nowUs();
  const int64_t mapped=pts_clock_update(&pts,0,stream,raw90k,now);
  if(mapped==PTS_CLOCK_INVALID)return true;
  if(firstMapped==PTS_CLOCK_INVALID||lastMapped==PTS_CLOCK_INVALID||
     mapped<lastMapped-90000*2LL||mapped>lastMapped+90000*10LL){
   firstMapped=mapped;anchorUs=now;
  }
  lastMapped=mapped;
  const int64_t due=anchorUs+(mapped-firstMapped)*100LL/9LL;
  int64_t diff=due-now;
  // The DVB tuner may change its PTS epoch during retuning/stream recovery.
  // Never freeze a player due to a 33-bit wrap or a timestamp discontinuity.
  if(diff>300000LL||diff< -2000000LL){
   firstMapped=mapped;anchorUs=now;
   return true;
  }
  if(lateUs&&diff<0)*lateUs= -diff;
  while(diff>2000&&!stopped.load(std::memory_order_relaxed)){
   std::this_thread::sleep_for(std::chrono::microseconds(
      diff>6000?3000:diff-1000));
   diff=due-nowUs();
  }
  return !stopped.load(std::memory_order_relaxed);
 }
};
