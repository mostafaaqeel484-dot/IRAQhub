package com.smarthub.plus;
/** In-process subtitle timeline, distinct from EPG wall-clock progress. */
final class CaptionClock {static volatile long positionMs;static volatile boolean playing;static volatile String text="";}
