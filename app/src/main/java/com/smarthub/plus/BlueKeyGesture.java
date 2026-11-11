package com.smarthub.plus;

/** Blue gestures use monotonic time and cancel pending actions when context changes. */
final class BlueKeyGesture {
 interface Clock {long now();void post(Runnable r,long delay);void cancel(Runnable r);}
 interface Listener {void single();void signal();void assistant();}
 static final long DOUBLE_MS=380,LONG_MS=650;
 private final Clock clock;private final Listener listener;private boolean held,longDone,pending,cancelled;private long firstRelease=-1;
 private final Runnable single,longPress;
 BlueKeyGesture(Clock c,Listener l){clock=c;listener=l;single=()->{if(pending&&!held){pending=false;firstRelease=-1;listener.single();}};longPress=()->{if(held&&!cancelled){longDone=true;pending=false;firstRelease=-1;clock.cancel(single);listener.assistant();}};}
 boolean down(){if(held)return true;held=true;cancelled=false;longDone=false;if(pending)clock.cancel(single);clock.post(longPress,LONG_MS);return true;}
 boolean up(){if(!held)return false;held=false;clock.cancel(longPress);if(cancelled||longDone){cancelled=false;longDone=false;return true;}long now=clock.now();if(pending&&firstRelease>=0&&now-firstRelease<=DOUBLE_MS){pending=false;firstRelease=-1;clock.cancel(single);listener.signal();}else{pending=true;firstRelease=now;clock.post(single,DOUBLE_MS);}return true;}
 void cancel(){clock.cancel(single);clock.cancel(longPress);pending=false;firstRelease=-1;cancelled=true;}
}
