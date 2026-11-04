package com.smarthub.plus;
/** Shared only inside this app process; no repeated preference writes for playback position. */
final class PlaybackClock{static volatile boolean visible;static volatile long positionMs;static volatile String channelId="";}
